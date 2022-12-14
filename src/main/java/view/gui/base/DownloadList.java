/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package view.gui.base;

import view.JFtp;
import controller.config.Settings;
import view.gui.base.dir.DirEntry;
import view.gui.base.dir.DirPanel;
import view.gui.framework.HImage;
import view.gui.framework.HImageButton;
import view.gui.framework.HPanel;
import view.gui.framework.ProgressBarList;
import model.net.ConnectionHandler;
import model.net.DataConnection;
import model.net.Transfer;
import model.net.wrappers.HttpTransfer;
import model.system.LocalIO;
import model.system.UpdateDaemon;
import model.system.logging.Log;
import controller.util.I18nHelper;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;


public class DownloadList extends HPanel implements ActionListener {
	public final Map<String, Long> sizeCache = new HashMap<>();
	private final ProgressBarList list = new ProgressBarList();
	private final JScrollPane scroll;
	private Map<String, DirEntry> downloads = new HashMap<>();
	private long oldtime;

	public DownloadList() {
		super();
		this.setLayout(new BorderLayout());

		HImageButton resume = new HImageButton(Settings.resumeImage, "resume", I18nHelper.getUIString("resume.selected.transfer"), this);
		resume.setRolloverIcon(new ImageIcon(HImage.getImage(this, Settings.resumeImage2)));
		resume.setRolloverEnabled(true);
		HImageButton pause = new HImageButton(Settings.pauseImage, "pause", I18nHelper.getUIString("pause.selected.transfer"), this);
		pause.setRolloverIcon(new ImageIcon(HImage.getImage(this, Settings.pauseImage2)));
		pause.setRolloverEnabled(true);
		HImageButton clear = new HImageButton(Settings.clearImage, "clear", I18nHelper.getUIString("remove.old.stalled.items.from.output"), this);
		clear.setRolloverIcon(new ImageIcon(HImage.getImage(this, Settings.clearImage2)));
		clear.setRolloverEnabled(true);
		HImageButton cancel = new HImageButton(Settings.deleteImage, "delete", I18nHelper.getUIString("cancel.selected.transfer"), this);
		cancel.setRolloverIcon(new ImageIcon(HImage.getImage(this, Settings.deleteImage2)));
		cancel.setRolloverEnabled(true);

		HPanel cmdP = new HPanel();

		cmdP.add(clear);

		cmdP.add(new JLabel("   "));

		cmdP.add(resume);
		cmdP.add(pause);

		cmdP.add(new JLabel("   "));

		cmdP.add(cancel);

		clear.setSize(24, 24);
		cancel.setSize(24, 24);
		resume.setSize(24, 24);
		pause.setSize(24, 24);

		clear.setToolTipText(I18nHelper.getUIString("remove.old.stalled.items.from.output"));

		resume.setToolTipText(I18nHelper.getUIString("resume.selected.transfer"));
		pause.setToolTipText(I18nHelper.getUIString("pause.selected.transfer"));
		cancel.setToolTipText(I18nHelper.getUIString("cancel.selected.transfer"));

		this.scroll = new JScrollPane(this.list);
		this.add(JFtp.SOUTH, cmdP);
		this.add(JFtp.CENTER, this.scroll);
	}

	public void fresh() {
		this.downloads = new HashMap<>();
		this.updateArea();
	}

	public void actionPerformed(ActionEvent e) {
		if (e.getActionCommand().equals("delete")) {
			this.deleteCon();
		} else if (e.getActionCommand().equals("clear") || (e.getSource() == AppMenuBar.clearItems)) {
			this.fresh();
		} else if (e.getActionCommand().equals("pause")) {
			this.pauseCon();
		} else if (e.getActionCommand().equals("resume")) {
			this.resumeCon();
		}
	}

	private void deleteCon() {
		try {
			String cmd = this.getActiveItem();

			if (null == cmd) {
				return;
			}

			if ((cmd.contains(Transfer.QUEUED)) || (cmd.contains(Transfer.PAUSED))) {
				cmd = this.getFile(cmd);

				try {
					Transfer d = JFtp.getConnectionHandler().getConnections().get(cmd);

					if (null == d) {
						return;
					}

					d.work = false;
					d.pause = false;

				} catch (Exception ex) {
					ex.printStackTrace();
				}
			} else {
				cmd = this.getFile(cmd);

				ConnectionHandler h = JFtp.getConnectionHandler();

				Object o = h.getConnections().get(cmd);

				Log.out(MessageFormat.format(I18nHelper.getLogString("aborting.transfer.0"), cmd));
				Log.out(MessageFormat.format(I18nHelper.getLogString("connection.handler.present.0.pool.size.1"), h, h.getConnections().size()));

				if (o instanceof HttpTransfer) {
					Transfer d = (Transfer) o;
					d.work = false;
					this.updateList(cmd, DataConnection.FAILED, -1, -1);

					return;
				} else {
					Transfer d = (Transfer) o;

					DataConnection con = d.getDataConnection();
					con.getCon().work = false;

					try {
						con.sock.close();

					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}

				LocalIO.pause(500);
				this.updateList(this.getRawFile(this.getActiveItem()), DataConnection.FAILED, -1, -1);
			}
		} catch (Exception ex) {
			Log.debug(I18nHelper.getLogString("action.is.not.supported.for.this.connection"));
			ex.printStackTrace();
		}
	}

	private void pauseCon() {
		try {
			String cmd = this.getActiveItem();

			if (null == cmd) {
				return;
			}

			if ((cmd.contains(DataConnection.GET)) || (cmd.contains(DataConnection.PUT))) {
				cmd = this.getFile(cmd);

				Object o = JFtp.getConnectionHandler().getConnections().get(cmd);

				if (null == o) {
					return;
				}

				Transfer d = (Transfer) o;
				d.pause = true;

				DataConnection con = d.getDataConnection();

				try {
					con.sock.close();
				} catch (Exception ex) {
					ex.printStackTrace();
				}

				d.prepare();
			}
		} catch (Exception ex) {
			Log.debug(I18nHelper.getLogString("action.is.not.supported.for.this.connection"));
		}
	}

	private void resumeCon() {
		try {
			String cmd = this.getActiveItem();

			if (null == cmd) {
				return;
			}

			if ((cmd.contains(Transfer.PAUSED)) || (cmd.contains(Transfer.QUEUED))) {
				cmd = this.getFile(cmd);

				try {
					Object o = JFtp.getConnectionHandler().getConnections().get(cmd);

					if (null == o) {
						return;
					}

					Transfer d = (Transfer) o;
					d.work = true;
					d.pause = false;
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		} catch (Exception ex) {
			Log.debug(I18nHelper.getLogString("action.is.not.supported.for.this.connection"));
		}
	}

	private String getActiveItem() {
		String tmp = this.list.getSelectedValue().getDirEntry().toString();

		if (null == tmp) {
			return "";
		} else {
			return tmp;
		}
	}

	public synchronized void updateList(String file, String type, long bytes, long size) {
		String message = type + ": <" + file + "> ";

		if (!this.safeUpdate()) {
			if (!type.startsWith(DataConnection.DFINISHED) && !type.startsWith(DataConnection.FINISHED) && !type.startsWith(DataConnection.FAILED) && !type.startsWith(Transfer.PAUSED) && !type.startsWith(Transfer.REMOVED)) {
				return;
			}
		}

		// directory
		int count = 0;

		if (type.startsWith(DataConnection.GETDIR) || type.startsWith(DataConnection.PUTDIR) || type.startsWith(DataConnection.DFINISHED)) {
			//System.out.println(type);
			String tmp = type.substring(type.indexOf(':') + 1);
			type = type.substring(0, type.indexOf(':'));
			count = Integer.parseInt(tmp);
			message = type + ": <" + file + "> ";
		}

		if (type.equals(DataConnection.GET)) {
			DirEntry[] e = ((DirPanel) JFtp.remoteDir).dirEntry;

			for (DirEntry dirEntry : e) {
				if (dirEntry.file.equals(file)) {
					size = dirEntry.getRawSize();
				}
			}
		}

		String tmp;
		long s = size / 1024;

		if (0 < s) {
			tmp = Long.toString(s);
		} else {
			tmp = "?";
		}

		if (type.equals(DataConnection.GET) || type.equals(DataConnection.PUT)) {
			message = MessageFormat.format(I18nHelper.getUIString("0.1.2.kb"), message, bytes / 1024, tmp);
			this.list.setTransferred(file, (bytes / 1024), message, s);
		} else if (type.equals(DataConnection.GETDIR) || type.equals(DataConnection.PUTDIR)) {
			message = MessageFormat.format(I18nHelper.getUIString("0.1.kb.of.file.2"), message, bytes / 1024, count);
			this.list.setTransferred(file, (bytes / 1024), message, -1);
		} else if (type.startsWith(DataConnection.DFINISHED)) {
			message = MessageFormat.format(I18nHelper.getUIString("0.1.files"), message, count);
		}

		if (type.equals(DataConnection.FINISHED) || type.startsWith(DataConnection.DFINISHED)) {
			try {
				JFtp.getConnectionHandler().removeConnection(file);
			} catch (RuntimeException ex) {
				Log.debug(I18nHelper.getLogString("downloadlist.runtimeexception.in.updatelist"));
			}

			UpdateDaemon.updateCall();
		} else if (type.equals(DataConnection.FAILED)) {
			UpdateDaemon.updateCall();
		}

		DirEntry d = null;
		if (this.downloads.containsKey(message)) {
			d = this.downloads.get(message);
		} else {
			d = new DirEntry(message, null);
			d.setNoRender();
			if (this.getFile(tmp).endsWith("/")) {
				d.setDirectory();
			}
			d.setFileSize(size);
		}

		d.setTransferred(bytes);

		this.downloads.put(file, d);

		this.updateArea();
	}

	private synchronized DirEntry[] toArray() {
		DirEntry[] f = new DirEntry[this.downloads.size()];
		int i = 0;

		for (Object o : this.downloads.values()) {
			if (o instanceof DirEntry) {
				f[i] = (DirEntry) o;
			} else {

				String tmp = (String) o;
				DirEntry d = new DirEntry(tmp, null);

				if (this.getFile(tmp).endsWith("/")) {
					d.setDirectory();
				}

				d.setNoRender();
				f[i] = d;
			}

			i++;
		}

		return f;
	}

	private synchronized void updateArea() {

		int idx = this.list.getSelectedIndex();

		DirEntry[] f = this.toArray();

		this.list.setListData(f);

		if ((1 == f.length) && (0 > idx)) {
			this.list.setSelectedIndex(0);
		} else {
			this.list.setSelectedIndex(idx);
		}

		this.revalidate();
		this.scroll.revalidate();
		this.repaint();
	}

	private String getFile(String msg) {
		String f = msg;

		if (msg.contains("<") && msg.contains(">")) {
			f = msg.substring(msg.indexOf('<') + 1);
			f = f.substring(0, f.lastIndexOf('>'));
		}

		return this.getRealName(f);
	}

	private String getRealName(String file) {
		try {

			for (String tmp : JFtp.getConnectionHandler().getConnections().keySet()) {
				if (tmp.endsWith(file)) {
					return tmp;
				}
			}
		} catch (RuntimeException ex) {
			Log.debug(I18nHelper.getLogString("downloadlist.runtimeexception.in.getrealname"));
		}

		return file;
	}

	private String getRawFile(String msg) {
		String f = msg.substring(msg.indexOf('<') + 1);
		f = f.substring(0, f.lastIndexOf('>'));

		return f;
	}

	private boolean safeUpdate() {
		long time = System.currentTimeMillis();

		if (Settings.refreshDelay > (time - this.oldtime)) {
			return false;
		}

		this.oldtime = time;

		return true;
	}
}
