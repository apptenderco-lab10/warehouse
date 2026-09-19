const { app, BrowserWindow, dialog, ipcMain, Notification, shell } = require('electron');
const path = require('path');
const fs = require('fs');

app.setName('APP HR Online');
app.setAppUserModelId('com.alppco.apphronline.windows');

let mainWindow = null;

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 900,
    minHeight: 650,
    show: false,
    backgroundColor: '#f4f8ff',
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true
    }
  });

  mainWindow.loadFile(path.join(__dirname, 'www', 'index.html'));

  mainWindow.once('ready-to-show', () => {
    mainWindow.maximize();
    mainWindow.show();
  });

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:\/\//i.test(url)) shell.openExternal(url);
    return { action: 'deny' };
  });

  mainWindow.webContents.on('will-navigate', (event, url) => {
    if (!url.startsWith('file://')) {
      event.preventDefault();
      if (/^https?:\/\//i.test(url)) shell.openExternal(url);
    }
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

ipcMain.on('apphr:save-base64', async (_event, payload) => {
  try {
    const fileName = String(payload?.fileName || 'APP-HR-Export.bin').replace(/[\\/:*?"<>|]/g, '-');
    const mimeType = String(payload?.mimeType || 'application/octet-stream');
    const base64Data = String(payload?.base64Data || '');

    const result = await dialog.showSaveDialog(mainWindow, {
      title: 'ذخیره فایل',
      defaultPath: fileName,
      filters: mimeType.includes('spreadsheetml')
        ? [{ name: 'Excel Workbook', extensions: ['xlsx'] }]
        : [{ name: 'All Files', extensions: ['*'] }]
    });

    if (result.canceled || !result.filePath) return;
    fs.writeFileSync(result.filePath, Buffer.from(base64Data, 'base64'));
  } catch (error) {
    dialog.showErrorBox('خطا در ذخیره فایل', error?.message || String(error));
  }
});

ipcMain.on('apphr:notify', (_event, payload) => {
  try {
    if (!Notification.isSupported()) return;
    const n = new Notification({
      title: String(payload?.title || 'APP HR Online'),
      body: String(payload?.body || ''),
      urgency: payload?.priority === 'important' ? 'critical' : 'normal'
    });
    n.on('click', () => {
      if (!mainWindow) return;
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.show();
      mainWindow.focus();
    });
    n.show();
  } catch (_) {}
});

app.whenReady().then(createWindow);

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) createWindow();
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
