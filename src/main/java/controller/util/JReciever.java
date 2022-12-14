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
package controller.util;

import model.system.LocalIO;

import javax.swing.*;
import java.io.DataInputStream;


class JReciever implements Runnable {
	private final byte[] buf = new byte[4048];
	private final DataInputStream in;

	public JReciever(DataInputStream in) {
		super();
		this.in = in;
		Thread reciever = new Thread(this);
		reciever.start();
	}

	public void run() {
		try {
			while (true) {
				int cnt = this.in.read(this.buf);

				if (-1 == cnt) {
					RawConnection.output.append(">>> Connection closed...");

					LocalIO.pause(100);

					JScrollBar bar = RawConnection.outputPane.getVerticalScrollBar();
					bar.setValue(bar.getMaximum());

					this.in.close();

					return;
				} else {
					String tmp = new String(this.buf);
					tmp = tmp.substring(0, cnt);

					RawConnection.output.append(tmp);
					LocalIO.pause(100);

					JScrollBar bar = RawConnection.outputPane.getVerticalScrollBar();
					bar.setValue(bar.getMaximum());

					LocalIO.pause(400);
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

}
