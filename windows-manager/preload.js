const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('Android', {
  saveBase64(fileName, mimeType, base64Data) {
    ipcRenderer.send('appgold:save-base64', { fileName, mimeType, base64Data });
  },
  setSession() {},
  clearSession() {},
  notifyAnnouncement(id, title, body, priority) {
    ipcRenderer.send('appgold:notify', { id, title, body, priority });
  }
});

contextBridge.exposeInMainWorld('ManagerDesktop', {
  isManagerDesktop() {
    return true;
  }
});
