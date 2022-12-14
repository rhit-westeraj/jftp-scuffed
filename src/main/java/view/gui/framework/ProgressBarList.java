package view.gui.framework;

import view.gui.base.dir.DirEntry;

import javax.swing.*;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ProgressBarList extends JPanel {

	private int index = -1;

	public ProgressBarList() {
		super();
		this.setLayout(new GridLayout(0, 1));

		this.addMouseListener(new MouseAdapter() {
			public void mouseReleased(MouseEvent e) {
				Component c = ProgressBarList.this.getComponentAt(e.getX(), e.getY());

				ProgressBarList.this.deselectAll();

				for (int i = 0; i < ProgressBarList.this.getComponentCount(); i++) {
					if (ProgressBarList.this.getComponent(i) instanceof ProgressbarItem) {
						ProgressbarItem item = (ProgressbarItem) ProgressBarList.this.getComponent(i);

						if (item == c) {
							item.select();
							ProgressBarList.this.index = i;
						}
					}
				}

				if (c instanceof ProgressbarItem) {

				}
			}
		});
	}

	public void setListData(DirEntry[] items) {

		this.removeAll();
		//System.out.println("\n\n--------------------\n");

		for (DirEntry item : items) {
			ProgressbarItem p = new ProgressbarItem(item);
			p.update((int) item.getTransferred() / 1024, (int) item.getRawSize() / 1024, item.file);

			//System.out.println("add: "+items[i].file+" -> "+items[i].getTransferred()+"/"+items[i].getRawSize());

			this.add(p);
		}

		while (10 > this.getComponentCount()) {
			this.add(new JLabel(" "));
		}

		this.revalidate();
		this.setSelectedIndex(this.index);
	}

	public ProgressbarItem getSelectedValue() {
		return (ProgressbarItem) this.getComponent(this.index);
	}

	public int getSelectedIndex() {
		return this.index;
	}

	public void setSelectedIndex(int idx) {

		this.deselectAll();

		this.index = idx;
		if (0 <= this.index && this.getComponentCount() > this.index && this.getComponent(this.index) instanceof ProgressbarItem) {
			((ProgressbarItem) this.getComponent(this.index)).select();
		}
	}

	private void deselectAll() {
		for (int i = 0; i < this.getComponentCount(); i++) {
			if (this.getComponent(i) instanceof ProgressbarItem) {
				((ProgressbarItem) this.getComponent(i)).deselect();
			}
		}
	}

	private String strip(String in) {
		String tmp;
		if (in.contains("<")) {
			in = in.substring(in.lastIndexOf('<') + 1);
			in = in.substring(0, in.lastIndexOf('>'));
		}

		return in;
	}

	public void setTransferred(String file, long bytes, String message, long max) {
		boolean ok = false;

		//System.out.println(file+":"+bytes+":"+max);

		for (int i = 0; i < this.getComponentCount() && !ok; i++) {
			if (this.getComponent(i) instanceof ProgressbarItem) {
				ProgressbarItem item = ((ProgressbarItem) this.getComponent(i));
				String f = this.strip(item.getDirEntry().file);

				if (f.equals(file)) {
					item.update(bytes, max, message);
					ok = true;
				}
			} else {
				ok = false;
			}
		}
	}

}
