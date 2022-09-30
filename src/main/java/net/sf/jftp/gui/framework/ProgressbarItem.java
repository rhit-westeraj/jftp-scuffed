package net.sf.jftp.gui.framework;

import javax.swing.*;
import java.awt.*;

public class ProgressbarItem extends JPanel {

	private final net.sf.jftp.gui.base.dir.DirEntry d;
	private final JProgressBar bar = new JProgressBar();

	public ProgressbarItem(final net.sf.jftp.gui.base.dir.DirEntry d) {
		this.d = d;
		this.setLayout(new BorderLayout(2, 2));

		this.add("Center", this.bar);

		this.bar.setStringPainted(true);
		this.bar.setString(d.file);

		this.setBackground(Color.WHITE);
		this.setSize(300, 25);
	}

	public void update(long size, long max, final String lbl) {

		while (max > Integer.MAX_VALUE) {
			max = max / 1000;
			size = size / 1000;
		}

		if (max > 0 && max >= size) {
			this.bar.setMaximum((int) max);
			this.bar.setValue((int) size);
		} else {
			this.bar.setMaximum(99999999);
			this.bar.setValue(0);
		}

		this.bar.setString(lbl);

		//System.out.println(""+bar.getValue()+":"+bar.getMaximum()+" -> "+lbl);
	}

	public net.sf.jftp.gui.base.dir.DirEntry getDirEntry() {
		return this.d;
	}

	public void deselect() {
		this.setBackground(Color.WHITE);
		this.repaint();
	}

	public void select() {
		this.setBackground(Color.LIGHT_GRAY);
		this.repaint();
	}

	public Insets getInsets() {
		final Insets in = super.getInsets();
		return new Insets(in.top + 3, in.left + 3, in.bottom + 3, in.right + 3);
	}

}
