const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('Android', {
  saveBase64(fileName, mimeType, base64Data) {
    ipcRenderer.send('apphr:save-base64', { fileName, mimeType, base64Data });
  },
  setSession() {
    // APP Gold persists the authenticated Supabase session in localStorage.
  },
  clearSession() {
    // The web app clears its own session state.
  },
  notifyAnnouncement(id, title, body, priority) {
    ipcRenderer.send('apphr:notify', { id, title, body, priority });
  }
});
