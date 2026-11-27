; Tauri NSIS installer hooks for Hotel Management System.
;
; Stops the app's own background processes before install/uninstall so that
; hotel-app-be.exe and the pgsql\bin binaries are not file-locked and can be
; replaced by the new image. Without this, a lingering sidecar from a previous
; session silently keeps the OLD backend in place after a reinstall.
;
; DATA SAFETY: the database lives in $LOCALAPPDATA\HotelApp\pgdata, outside
; $INSTDIR. These hooks never delete or modify it. PostgreSQL is stopped with
; "pg_ctl stop -m fast" (clean shutdown); only processes whose executable path
; is inside $INSTDIR are ever force-killed, so an unrelated PostgreSQL on the
; machine is untouched.

!macro _hotel_stop_services
  ; Stop the backend sidecar first (it holds connections to PostgreSQL).
  ; The image name is unique to this app, so matching by name is safe.
  nsExec::ExecToLog `"$SYSDIR\taskkill.exe" /F /T /IM hotel-app-be.exe`
  ; Gracefully stop the embedded PostgreSQL (clean shutdown, data preserved).
  IfFileExists "$INSTDIR\pgsql\bin\pg_ctl.exe" 0 +2
    nsExec::ExecToLog `"$INSTDIR\pgsql\bin\pg_ctl.exe" stop -D "$LOCALAPPDATA\HotelApp\pgdata" -m fast -w -t 30`
  ; Clean up orphaned postgres processes from this install dir (if any remain
  ; after the graceful stop). PostgreSQL is crash-safe via WAL, and pgdata
  ; itself is never touched. Pass $INSTDIR via environment variable so quotes
  ; or wildcard brackets in the install path cannot break PowerShell parsing.
  System::Call 'Kernel32::SetEnvironmentVariable(t "HOTEL_INSTDIR", t "$INSTDIR")'
  nsExec::ExecToLog `"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -Command "Get-Process postgres -ErrorAction SilentlyContinue | Where-Object { $$_.Path -and $$_.Path.StartsWith($$env:HOTEL_INSTDIR + '\', [System.StringComparison]::OrdinalIgnoreCase) } | Stop-Process -Force"`
  ; Give Windows a moment to release file locks after process termination.
  Sleep 2000
!macroend

!macro NSIS_HOOK_PREINSTALL
  !insertmacro _hotel_stop_services
!macroend

!macro NSIS_HOOK_PREUNINSTALL
  !insertmacro _hotel_stop_services
!macroend
