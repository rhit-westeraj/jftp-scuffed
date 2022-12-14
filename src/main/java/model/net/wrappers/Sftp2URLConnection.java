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
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package model.net.wrappers;

import model.system.logging.Log;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;


public class Sftp2URLConnection extends URLConnection {
	private Sftp2Connection connection;
	private String username = "user";
	private String password = "none@no.no";
	private boolean loginFlag;

	public Sftp2URLConnection(URL u) {
		super(u);

		int port = 0 < u.getPort() ? u.getPort() : 22;
		this.connection = new Sftp2Connection(u.getHost(), String.valueOf(port), null);

		String userInfo = u.getUserInfo();

		if (null != userInfo) {
			int index = userInfo.indexOf(':');

			if (-1 != index) {
				this.username = userInfo.substring(0, index);
				this.password = userInfo.substring(index + 1);
			}
		}

		Log.debug("Connecting...");
	}

	public void connect() throws IOException {
		this.loginFlag = this.connection.login(this.username, this.password);

		if (!this.loginFlag) {
			return;
		}

		this.connection.chdir(this.url.getPath());
	}

	public Sftp2Connection getSftp2Connection() {
		return this.connection;
	}

	public String getUser() {
		return this.username;
	}

	public String getPass() {
		return this.password;
	}

	public String getHost() {
		return this.url.getHost();
	}

	public int getPort() {
		int ret = this.url.getPort();

		if (0 >= ret) {
			return 22;
		} else {
			return ret;
		}
	}

	public boolean loginSucceeded() {
		return this.loginFlag;
	}

}
