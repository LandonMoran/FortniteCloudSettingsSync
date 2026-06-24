# Fortnite Cloud Storage Research Document

## Overview
This document compiles research on Fortnite's cloud storage system, specifically focusing on the `ClientSettings.sav` file and how the Fortnite Cloud Settings Sync application interacts with Epic Games' cloud storage API.

---

## 1. Fortnite ClientSettings.sav File

### Purpose
The `ClientSettings.sav` file stores user-specific game settings for Fortnite, including:
- Keybindings/keyboard mappings
- Mouse sensitivity settings  
- Video/graphics settings
- Audio settings
- UI preferences
- Gameplay options (like aim assist, crosshair style, etc.)

### File Location (Local)
On PC, the file is typically located in:
```
%LOCALAPPDATA%\FortniteGame\Saved\Config\WindowsClient\ClientSettings.sav
```

### Cloud Storage Integration
Fortnite allows users to sync their settings across devices via Epic Games' cloud storage. This means:
- Settings made on PC can sync to console (with limitations)
- Users can backup and restore settings
- Settings can be shared between accounts

---

## 2. Epic Games Cloud Storage API

### Endpoints Used

Based on code analysis of the Fortnite Cloud Settings Sync application and related tools:

#### Base URLs
- **Account Service**: `https://account-public-service-prod03.ol.epicgames.com/`
- **Fortnite Service**: `https://fortnite-public-service-prod11.ol.epicgames.com/`

#### Authentication
The app uses OAuth 2.0 with the following credentials:
- **Client ID**: `ec684b8c687f479fadea3cb2ad83f5c6`
- **Client Secret**: `e1f31c211f28413186262d37a13fc84d`

#### Authorization Flow
1. User visits Epic Games authorization URL
2. User logs in and grants permission
3. User receives an authorization code
4. Code is exchanged for access token and account ID
5. Access token is used for subsequent API calls

#### Key API Endpoints

**Authentication:**
```
POST /account/api/oauth/token
- grant_type: authorization_code
- code: [authorization code]
- token_type: eg1
```

**Cloud Storage Operations:**
```
GET  /fortnite/api/cloudstorage/user/{accountId}
     - Lists all cloud storage files

GET  /fortnite/api/cloudstorage/user/{accountId}/{filename}
     - Downloads a specific file

PUT  /fortnite/api/cloudstorage/user/{accountId}/{filename}
     - Uploads/updates a file

DELETE /fortnite/api/cloudstorage/user/{accountId}/{filename}
     - Deletes a file
```

---

## 3. File Types in Fortnite Cloud Storage

### Known Files
1. **ClientSettings.sav** - Main settings file (Windows)
2. **ClientSettingsIOS.sav** - iOS device settings
3. **ClientSettings.Android.sav** - Android device settings  
4. **ClientSettingsSwitch.Sav** - Nintendo Switch settings (RESTRICTED)

### File Naming Patterns
The codebase filters out files matching UUID patterns:
```
Pattern: [0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}_r\d+_a\d+\.sav
Example: a1b2c3d4-e5f6-7890-abcd-ef1234567890_r1_a1.sav
```

These UUID-pattern files appear to be temporary or platform-specific files that should be filtered out.

---

## 4. Related Open Source Projects

### FortniteResponseDumper (PRO100KatYT)
A Python tool that dumps various Fortnite API responses including cloud storage.

**Features:**
- Multiple account support (refresh token and device auth)
- Dumps account and global cloudstorage
- Dumps user profiles, BR stats, friends, catalog, etc.

**Cloud Storage URL Used:**
```
https://fngw-mcp-gc-livefn.ol.epicgames.com/fortnite/api/cloudstorage/{0}
```

### SettingsReader (razfriman)  
A C# project using CUE4Parse to read Fortnite ClientSettings files.

**Purpose:**
- Parses the binary .sav format
- Extracts readable settings from the encrypted/binary format

---

## 5. ClientSettings.sav File Format

### Technical Details
Based on research and related projects:

1. **Format Type**: Binary/serialized format (not plain JSON or text)

2. **Structure** (inferred):
   - Header with version information
   - Serialized game settings in a structured format
   - Likely uses UE4's custom serialization

3. **Encryption/Signing**:
   - Files may have checksums or signatures
   - Epic may validate file integrity on upload

4. **Reading/Writing**:
   - Requires understanding of Unreal Engine's serialization
   - The CUE4Parse library is commonly used for parsing

---

## 6. Cloud Storage Limitations & Notes

### Observed Behaviors
1. **File Size Limits**: Unknown exact limits, but typical settings files are small (<100KB)

2. **File Replacement**: Uploading a file with the same name automatically replaces the existing file

3. **Restricted Files**: 
   - Some platform-specific files (like Switch) cannot be accessed
   - UUID-pattern files are filtered client-side

4. **Authentication Persistence**:
   - Access tokens expire (typically 30 days for refresh tokens)
   - Device auth credentials don't expire but may trigger password changes

### Platform Considerations
- **PC to PC**: Full settings sync
- **PC to Console**: May have limitations due to different input methods
- **Cross-platform**: Some settings may not transfer due to platform differences

---

## 7. Security Considerations

### Credentials (DO NOT SHARE)
The Fortnite Cloud Settings Sync app uses Epic Games OAuth credentials:
- Client ID: `ec684b8c687f479fadea3cb2ad83f5c6`
- Client Secret: `e1f31c211f28413186262d37a13fc84d`

**Note**: These are publicly known as they are embedded in the Fortnite game client itself for OAuth authentication.

### Best Practices
1. Never expose access tokens in logs or error messages
2. Store tokens securely (though this app doesn't implement persistence in the simplified version)
3. Use HTTPS for all API calls
4. Validate file names before upload
5. Filter out restricted files as per Epic's guidelines

---

## 8. API Response Formats

### List Files Response
The API returns files in various formats:

**Format 1 (Array):**
```json
[
  {
    "uniqueFilename": "ClientSettings.sav",
    "length": 12345,
    "lastModified": "2024-01-15T10:30:00.000Z"
  }
]
```

**Format 2 (Object with files key):**
```json
{
  "files": [
    {
      "uniqueFilename": "ClientSettings.sav",
      "length": 12345,
      "lastModified": "2024-01-15T10:30:00.000Z"
    }
  ]
}
```

### Error Responses
```json
{
  "errorMessage": "Something went wrong"
}
```

---

## 9. Future Research Topics

### To Investigate Further:
1. **File Format Parsing**: How to properly read/write the binary .sav format
2. **Settings Validation**: Whether Epic validates settings before accepting uploads
3. **Sync Behavior**: How Epic handles conflicts when multiple devices modify settings
4. **Platform Limits**: What are the exact storage limits per account
5. **Alternative Auth**: Using device auth for longer-lived sessions
6. **Settings Templates**: Creating default/template settings files for sharing

---

## 10. References

- FortniteResponseDumper: https://github.com/PRO100KatYT/FortniteResponseDumper
- SettingsReader (CUE4Parse): https://github.com/razfriman/SettingsReader
- Epic Games Developer Portal: https://dev.epicgames.com/

---

*Document compiled: June 2024*
*Note: Fortnite and Epic Games are registered trademarks of Epic Games, Inc. This research is for educational purposes only.*
