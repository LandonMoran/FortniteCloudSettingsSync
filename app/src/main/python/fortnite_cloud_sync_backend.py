"""Python backend for the Android app.

This keeps the Epic Games auth/cloud-storage logic in Python so the Android
app can call the same networking code path from Kotlin via Chaquopy.
"""

from __future__ import annotations

import base64
import json
import re
from pathlib import Path
from typing import Dict, List, Optional, Tuple
from urllib.parse import parse_qs, quote, urlparse

import requests


CLIENT_ID = "ec684b8c687f479fadea3cb2ad83f5c6"
CLIENT_SECRET = "e1f31c211f28413186262d37a13fc84d"
ACCOUNT_BASE_URL = "https://account-public-service-prod03.ol.epicgames.com"
FORTNITE_BASE_URL = "https://fortnite-public-service-prod11.ol.epicgames.com"
AUTHORIZATION_URL = (
    "https://www.epicgames.com/id/logout?redirectUrl=https%3A//www.epicgames.com/id/login%3F"
    "redirectUrl%3Dhttps%253A//www.epicgames.com/id/api/redirect%253F"
    "clientId%253Dec684b8c687f479fadea3cb2ad83f5c6%2526responseType%253Dcode"
)

RESTRICTED_FILES = {"ClientSettingsSwitch.Sav"}
UUID_PATTERN = re.compile(
    r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}_r\d+_a\d+\.sav$",
    re.IGNORECASE,
)
CODE_QUERY_PATTERN = re.compile(r"(?:^|[?&#])code=([^&\s#]+)", re.IGNORECASE)
AUTH_CODE_FIELD_PATTERN = re.compile(
    r"""["']authorizationCode["']\s*:\s*["']([^"']+)["']""",
    re.IGNORECASE,
)
REDIRECT_URL_FIELD_PATTERN = re.compile(
    r"""["']redirectUrl["']\s*:\s*["']([^"']+)["']""",
    re.IGNORECASE,
)
RAW_CODE_PATTERN = re.compile(r"^[a-zA-Z0-9]{20,40}$")
FALLBACK_RAW_CODE_PATTERN = re.compile(r"^[A-Za-z0-9._~-]{20,512}$")

# Request timeout: (connect, read) seconds, matching original OkHttp settings.
_REQUEST_TIMEOUT = (30, 60)


def _auth_header() -> str:
    auth_string = f"{CLIENT_ID}:{CLIENT_SECRET}"
    return base64.b64encode(auth_string.encode()).decode()


def _json_result(**kwargs) -> str:
    return json.dumps(kwargs)


def extract_code_from_url(url_or_code: str) -> str:
    if not url_or_code:
        return ""

    try:
        parsed_url = urlparse(url_or_code)
        query_params = parse_qs(parsed_url.query)
        if "code" in query_params and query_params["code"]:
            return query_params["code"][0]
    except Exception:
        pass

    match = CODE_QUERY_PATTERN.search(url_or_code)
    if match:
        return match.group(1)

    return ""


def _extract_code_payload(payload: str) -> str:
    if not payload:
        return ""

    trimmed = payload.strip()

    if trimmed.startswith("{") and trimmed.endswith("}"):
        try:
            json_data = json.loads(trimmed)
            if json_data.get("authorizationCode"):
                return str(json_data["authorizationCode"])
            redirect_url = json_data.get("redirectUrl")
            if redirect_url:
                code = extract_code_from_url(str(redirect_url))
                if code:
                    return code
        except json.JSONDecodeError:
            pass

    if trimmed.startswith("http"):
        code = extract_code_from_url(trimmed)
        if code:
            return code

    auth_code_match = AUTH_CODE_FIELD_PATTERN.search(trimmed)
    if auth_code_match:
        return auth_code_match.group(1)

    redirect_url_match = REDIRECT_URL_FIELD_PATTERN.search(trimmed)
    if redirect_url_match:
        code = extract_code_from_url(redirect_url_match.group(1))
        if code:
            return code

    if RAW_CODE_PATTERN.fullmatch(trimmed):
        return trimmed

    if FALLBACK_RAW_CODE_PATTERN.fullmatch(trimmed):
        return trimmed

    return ""


def extract_code_from_input(url_or_code: str) -> str:
    if not url_or_code:
        return ""

    trimmed = url_or_code.strip()
    code = _extract_code_payload(trimmed)
    if code:
        return code

    compact = re.sub(r"\s+", "", trimmed)
    if compact != trimmed:
        code = _extract_code_payload(compact)
        if code:
            return code

    return ""


class EpicGamesAuth:
    """Handle Epic Games authentication."""

    def __init__(self) -> None:
        self.client_id = CLIENT_ID
        self.client_secret = CLIENT_SECRET
        self.access_token: Optional[str] = None
        self.account_id: Optional[str] = None
        self.refresh_token: Optional[str] = None

    def clear_session(self) -> None:
        self.access_token = None
        self.account_id = None
        self.refresh_token = None

    def get_auth_header(self) -> str:
        return _auth_header()

    def get_authorization_url(self) -> str:
        return AUTHORIZATION_URL

    def exchange_code_login(self, exchange_code: str) -> Tuple[bool, str]:
        url = f"{ACCOUNT_BASE_URL}/account/api/oauth/token"
        headers = {
            "Authorization": f"basic {self.get_auth_header()}",
            "Content-Type": "application/x-www-form-urlencoded",
        }
        data = {
            "grant_type": "authorization_code",
            "code": exchange_code,
            "token_type": "eg1",
        }

        try:
            response = requests.post(url, headers=headers, data=data, timeout=_REQUEST_TIMEOUT)
            if response.status_code == 200:
                result = response.json()
                self.access_token = result.get("access_token")
                self.account_id = result.get("account_id")
                self.refresh_token = result.get("refresh_token")

                if self.access_token and self.account_id:
                    return True, f"Authentication successful! Account ID: {self.account_id}"
                return False, "Authentication failed: Missing token or account ID"

            try:
                error_data = response.json()
                error_msg = error_data.get("errorMessage", "Unknown error")
            except Exception:
                error_msg = response.text
            return False, f"Authentication failed: {response.status_code}\nError: {error_msg}"
        except requests.RequestException as exc:
            return False, f"Network error during authentication: {exc}"

    def verify_token(self) -> bool:
        if not self.access_token:
            return False

        url = f"{ACCOUNT_BASE_URL}/account/api/oauth/verify"
        headers = {"Authorization": f"bearer {self.access_token}"}

        try:
            response = requests.get(url, headers=headers, timeout=_REQUEST_TIMEOUT)
            return response.status_code == 200
        except requests.RequestException:
            return False


class FortniteCloudStorage:
    """Handle Fortnite cloud storage operations."""

    def __init__(self, auth: EpicGamesAuth):
        self.auth = auth
        self.base_url = FORTNITE_BASE_URL
        self.restricted_files = set(RESTRICTED_FILES)
        self.uuid_pattern = UUID_PATTERN
        self.blacklisted_patterns: List[str] = []

    def format_size(self, bytes_count: int) -> str:
        if bytes_count >= 1024 * 1024:
            return f"{bytes_count / (1024 * 1024):.1f} MB"
        if bytes_count >= 1024:
            return f"{bytes_count / 1024:.1f} KB"
        return f"{bytes_count} bytes"

    def is_file_allowed(self, filename: str) -> bool:
        if filename in self.restricted_files:
            return False
        if self.uuid_pattern.match(filename):
            return False
        for pattern in self.blacklisted_patterns:
            if re.search(pattern, filename, re.IGNORECASE):
                return False
        return True

    def list_files_data(self, filter_restricted: bool = True) -> Tuple[bool, str, List[Dict], int]:
        if not self.auth.access_token or not self.auth.account_id:
            return False, "Authentication required", [], 0

        url = f"{self.base_url}/fortnite/api/cloudstorage/user/{self.auth.account_id}"
        headers = {"Authorization": f"bearer {self.auth.access_token}"}

        try:
            response = requests.get(url, headers=headers, timeout=_REQUEST_TIMEOUT)
            response.raise_for_status()

            data = response.json()
            if isinstance(data, list):
                files = data
            elif isinstance(data, dict):
                if "files" in data:
                    files = data["files"]
                elif "data" in data:
                    files = data["data"] if isinstance(data["data"], list) else [data["data"]]
                elif "items" in data:
                    files = data["items"]
                else:
                    if "uniqueFilename" in data:
                        files = [data]
                    else:
                        return True, f"Unexpected response format: {list(data.keys())}", [], 0
            else:
                return True, f"Unexpected response type: {type(data)}", [], 0

            original_count = len(files)
            if filter_restricted:
                files = [f for f in files if self.is_file_allowed(f.get("uniqueFilename", ""))]
            filtered_count = original_count - len(files)

            if filtered_count > 0:
                return (
                    True,
                    f"Found {len(files)} files in cloud storage (filtered {filtered_count} restricted files)",
                    files,
                    filtered_count,
                )
            return True, f"Found {len(files)} files in cloud storage", files, 0
        except requests.RequestException as exc:
            error_msg = f"Failed to list cloud storage files: {exc}"
            if hasattr(exc, "response") and exc.response is not None:
                error_msg += f"\nStatus: {exc.response.status_code}\nResponse: {exc.response.text}"
            return False, error_msg, [], 0

    def list_files(self, filter_restricted: bool = True) -> Tuple[bool, str, List[Dict]]:
        success, message, files, _ = self.list_files_data(filter_restricted)
        return success, message, files

    def _download_file_bytes(self, unique_filename: str) -> Tuple[bool, str, bytes]:
        if not self.auth.access_token or not self.auth.account_id:
            return False, "Authentication required", b""
        if not self.is_file_allowed(unique_filename):
            return False, f"File {unique_filename} is restricted and cannot be downloaded", b""

        encoded_filename = quote(unique_filename, safe="")
        url = f"{self.base_url}/fortnite/api/cloudstorage/user/{self.auth.account_id}/{encoded_filename}"
        headers = {"Authorization": f"bearer {self.auth.access_token}"}

        try:
            response = requests.get(url, headers=headers, timeout=_REQUEST_TIMEOUT)
            response.raise_for_status()
            return True, f"Downloaded {unique_filename} ({self.format_size(len(response.content))})", response.content
        except requests.RequestException as exc:
            return False, f"Download failed: {exc}", b""

    def download_file_to_bytes(self, unique_filename: str) -> bytes:
        success, message, data = self._download_file_bytes(unique_filename)
        if not success:
            raise RuntimeError(message)
        return data

    def download_file(self, unique_filename: str, local_path: str) -> Tuple[bool, str]:
        success, message, data = self._download_file_bytes(unique_filename)
        if not success:
            return False, message

        try:
            with open(local_path, "wb") as file_handle:
                file_handle.write(data)
            return True, f"{message}\nSaved to: {local_path}"
        except OSError as exc:
            return False, f"Failed to save file: {exc}"

    def _upload_file_bytes(self, unique_filename: str, file_data: bytes) -> Tuple[bool, str]:
        if not self.auth.access_token or not self.auth.account_id:
            return False, "Authentication required"

        if not self.is_file_allowed(unique_filename):
            return False, f"File {unique_filename} is restricted and cannot be modified"

        if not isinstance(file_data, (bytes, bytearray)):
            file_data = bytes(file_data)

        encoded_filename = quote(unique_filename, safe="")
        url = f"{self.base_url}/fortnite/api/cloudstorage/user/{self.auth.account_id}/{encoded_filename}"
        headers = {
            "Authorization": f"bearer {self.auth.access_token}",
            "Content-Type": "application/octet-stream",
        }

        try:
            file_exists = any(
                file.get("uniqueFilename") == unique_filename
                for file in self.list_files_data(filter_restricted=False)[2]
            )
            response = requests.put(url, headers=headers, data=file_data, timeout=_REQUEST_TIMEOUT)
            response.raise_for_status()

            action = "Replaced" if file_exists else "Uploaded"
            return True, f"{action} {unique_filename} ({self.format_size(len(file_data))})\nStatus: {response.status_code}"
        except requests.RequestException as exc:
            error_msg = f"Upload failed: {exc}"
            if hasattr(exc, "response") and exc.response is not None:
                error_msg += f"\nStatus: {exc.response.status_code}\nResponse: {exc.response.text}"
            return False, error_msg

    def upload_file_from_bytes(self, unique_filename: str, file_data: bytes) -> str:
        success, message = self._upload_file_bytes(unique_filename, file_data)
        if not success:
            raise RuntimeError(message)
        return message

    def upload_file(self, local_path: str, unique_filename: str) -> Tuple[bool, str]:
        if not Path(local_path).exists():
            return False, f"Local file not found: {local_path}"

        try:
            with open(local_path, "rb") as file_handle:
                file_data = file_handle.read()
        except OSError as exc:
            return False, f"Failed to read local file: {exc}"

        return self._upload_file_bytes(unique_filename, file_data)

    def delete_file(self, unique_filename: str) -> Tuple[bool, str]:
        if not self.auth.access_token or not self.auth.account_id:
            return False, "Authentication required"

        if not self.is_file_allowed(unique_filename):
            return False, f"File {unique_filename} is restricted and cannot be deleted"

        encoded_filename = quote(unique_filename, safe="")
        url = f"{self.base_url}/fortnite/api/cloudstorage/user/{self.auth.account_id}/{encoded_filename}"
        headers = {"Authorization": f"bearer {self.auth.access_token}"}

        try:
            response = requests.delete(url, headers=headers, timeout=_REQUEST_TIMEOUT)
            response.raise_for_status()
            return True, f"Deleted {unique_filename}\nStatus: {response.status_code}"
        except requests.RequestException as exc:
            error_msg = f"Deletion failed: {exc}"
            if hasattr(exc, "response") and exc.response is not None:
                error_msg += f"\nStatus: {exc.response.status_code}\nResponse: {exc.response.text}"
            return False, error_msg


AUTH = EpicGamesAuth()
CLOUD = FortniteCloudStorage(AUTH)


def clear_session() -> None:
    AUTH.clear_session()


def get_authorization_url() -> str:
    return AUTH.get_authorization_url()


def exchange_code_login_json(exchange_code: str) -> str:
    success, message = AUTH.exchange_code_login(exchange_code)
    return _json_result(
        success=success,
        message=message,
        access_token=AUTH.access_token,
        account_id=AUTH.account_id,
        refresh_token=AUTH.refresh_token,
    )


def verify_token() -> bool:
    return AUTH.verify_token()


def list_files_json(filter_restricted: bool = True) -> str:
    success, message, files, filtered_count = CLOUD.list_files_data(filter_restricted)
    return _json_result(
        success=success,
        message=message,
        files=files,
        filtered_count=filtered_count,
    )


def download_file_bytes(unique_filename: str) -> bytes:
    return CLOUD.download_file_to_bytes(unique_filename)


def upload_file_bytes(unique_filename: str, file_data: bytes) -> str:
    return CLOUD.upload_file_from_bytes(unique_filename, file_data)


def delete_file(unique_filename: str) -> str:
    success, message = CLOUD.delete_file(unique_filename)
    if not success:
        raise RuntimeError(message)
    return message


def format_size(bytes_count: int) -> str:
    return CLOUD.format_size(bytes_count)
