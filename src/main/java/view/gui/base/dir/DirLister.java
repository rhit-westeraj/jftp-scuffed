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
package view.gui.base.dir;

import controller.config.Settings;
import model.net.BasicConnection;
import model.net.FilesystemConnection;
import model.net.FtpConnection;
import model.system.LocalIO;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


public class DirLister implements ActionListener {
	private final BasicConnection con;
	public boolean finished;
	private int length;
	private String[] files;
	private String[] sizes;
	private int[] perms;
	private boolean isDirectory = true;
	private String sortMode;
	private LocalDateTime[] dates;

	public DirLister(BasicConnection con) {
		super();
		this.con = con;
		this.init();
	}

	public DirLister(BasicConnection con, String sortMode) {
		super();
		this.con = con;
		this.sortMode = sortMode;
		this.init();
	}

	public DirLister(BasicConnection con, String sortMode, boolean hide) {
		super();
		this.con = con;
		this.sortMode = sortMode;
		this.init();

		int cnt = this.files.length;

		if (hide) {
			for (int i = 0; i < this.files.length; i++) {
				if (this.files[i].startsWith(".") && !this.files[i].startsWith("..")) {
					this.files[i] = null;
					cnt--;
				}
			}

			String[] newFiles = new String[cnt];
			String[] newSizes = new String[cnt];
			int[] newPerms = new int[cnt];

			int idx = 0;
			for (int i = 0; i < this.files.length; i++) {
				if (null == this.files[i]) {
				} else {
					newFiles[idx] = this.files[i];
					newSizes[idx] = this.sizes[i];
					newPerms[idx] = this.perms[i];
					idx++;
				}
			}

			this.files = newFiles;
			this.sizes = newSizes;
			this.perms = newPerms;
			this.length = this.files.length;
		}
	}

	private void init() {
		try {
			String outfile = Settings.ls_out;

			this.con.list();
			this.files = this.con.sortLs();
			this.sizes = this.con.sortSize();

			this.length = this.files.length;
			this.perms = this.con.getPermissions();
			this.isDirectory = true;

			if (null != this.sortMode) {
				if (!this.sortMode.equals("Date")) {
					this.sortFirst();
				}

				this.sort(this.sortMode);
			} else if (null == this.sortMode) {
				this.sortFirst();
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			this.isDirectory = false;
		}

		this.finished = true;
	}

	private void sort(String type) {
		List<String> fv = new ArrayList<>();
		List<String> sv = new ArrayList<>();
		List<Integer> pv = new ArrayList<>();

		if (type.equals("Reverse")) {
			for (int i = 0; i < this.length; i++) {
				fv.add(this.files[i]);
				sv.add(this.sizes[i]);

				if (null != this.perms) {
					pv.add(this.perms[i]);
				}
			}

			fv.sort(java.util.Collections.reverseOrder());

			Object[] filesTmp = fv.toArray();
			Object[] sizesTmp = sv.toArray();
			Object[] permsTmp = null;

			if (null != this.perms) {
				permsTmp = pv.toArray();
			}

			for (int i = 0; i < this.length; i++) {
				this.files[i] = (String) filesTmp[i];
				this.sizes[i] = (String) sizesTmp[this.length - i - 1];

				if (null != this.perms) {
					this.perms[i] = ((int) permsTmp[this.length - i - 1]);
				}
			}
		} else if (type.startsWith("Size")) {
			int cnt = 0;
			java.util.Map<Integer, String> processed = new java.util.HashMap<>();
			boolean reverse = type.endsWith("/Re");

			while (cnt < this.length) {
				int idx = 0;
				double current = 0;

				if (reverse) {
					current = Double.MAX_VALUE;
				}

				for (int i = 0; i < this.length; i++) {
					if (processed.containsKey(String.valueOf(i))) {
						continue;
					}

					int si = Integer.parseInt(this.sizes[i]);

					if (!reverse && (si >= current)) {
						idx = i;
						current = si;
					} else if (reverse && (si <= current)) {
						idx = i;
						current = si;
					}
				}

				processed.put(idx, this.sizes[idx]);
				fv.add(this.files[idx]);
				sv.add(this.sizes[idx]);

				if (null != this.perms) {
					pv.add(this.perms[idx]);
				}

				cnt++;
			}

			for (int i = 0; i < this.length; i++) {
				this.files[i] = fv.get(i);
				this.sizes[i] = sv.get(i);

				if (null != this.perms) {
					this.perms[i] = pv.get(i);
				}
			}
		} else if (type.equals("Date")) {
			String style = "ftp";

			//TODO: may be slow
			if (!(this.con instanceof FtpConnection) || (null == ((FtpConnection) this.con).dateVector) || (1 > ((FtpConnection) this.con).dateVector.size())) {
				if (!(this.con instanceof FilesystemConnection) || (null == ((FilesystemConnection) this.con).dateVector) || (1 > ((FilesystemConnection) this.con).dateVector.size())) {
				} else {
					style = "file";
				}
			}

			LocalDateTime[] date = null;

			if (style.equals("ftp")) {
				date = ((FtpConnection) this.con).dateVector.toArray(date);

			} else {
				date = ((FilesystemConnection) this.con).dateVector.toArray(date);
			}

			for (int j = 0; j < date.length; j++) {
				for (int i = 0; i < date.length; i++) {
					LocalDateTime x = date[i];

					if (i == (date.length - 1)) {
						break;
					}

					if (this.comp(x, date[i + 1])) {
						LocalDateTime swp = date[i + 1];
						date[i + 1] = x;
						date[i] = swp;

						String s1 = this.files[i + 1];
						String s2 = this.files[i];
						this.files[i] = s1;
						this.files[i + 1] = s2;

						s1 = this.sizes[i + 1];
						s2 = this.sizes[i];
						this.sizes[i] = s1;
						this.sizes[i + 1] = s2;

						int s3 = this.perms[i + 1];
						int s4 = this.perms[i];
						this.perms[i] = s3;
						this.perms[i + 1] = s4;
					}
				}
			}

			this.dates = new LocalDateTime[date.length];

			for (int i = 0; i < this.dates.length; i++) {
				this.dates[i] = date[i];
			}

		} else if (type.equals("Normal")) {
			// already done.
		}
	}

	private boolean comp(LocalDateTime one, LocalDateTime two) {
		return 0 < one.compareTo(two);
	}

	private void sortFirst() {
		String[] tmpx = new String[this.length];

		for (int x = 0; x < this.length; x++) {
			if (null != this.perms) {
				tmpx[x] = this.files[x] + "@@@" + this.sizes[x] + "@@@" + this.perms[x];

			} else {
				tmpx[x] = this.files[x] + "@@@" + this.sizes[x];
			}
		}

		LocalIO.sortStrings(tmpx);

		for (int y = 0; y < this.length; y++) {
			this.files[y] = tmpx[y].substring(0, tmpx[y].indexOf("@@@"));

			String tmp = tmpx[y].substring(tmpx[y].indexOf("@@@") + 3);
			this.sizes[y] = tmp.substring(0, tmp.lastIndexOf("@@@"));

			if (null != this.perms) {
				this.perms[y] = Integer.parseInt(tmpx[y].substring(tmpx[y].lastIndexOf("@@@") + 3));
			}
		}
	}

	public void actionPerformed(ActionEvent e) {
	}

	public boolean isOk() {
		return this.isDirectory;
	}

	public int getLength() {
		return this.length;
	}

	public String[] list() {
		return this.files;
	}

	public String[] sList() {
		return this.sizes;
	}

	public int[] getPermissions() {
		return this.perms;
	}

	public LocalDateTime[] getDates() {
		return this.dates;
	}
}
