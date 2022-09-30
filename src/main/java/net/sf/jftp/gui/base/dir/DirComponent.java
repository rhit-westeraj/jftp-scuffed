package net.sf.jftp.gui.base.dir;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.util.Vector;


public class DirComponent extends DirPanel implements ListSelectionListener {

	public final JTable table = new JTable();
	DirPanel target;

	public DirComponent() {

		this.table.setDefaultRenderer(Object.class, new ColoredCellRenderer());
		this.table.getSelectionModel().addListSelectionListener(this);

		this.table.setRowSelectionAllowed(true);
		this.table.setColumnSelectionAllowed(false);
	}

	public void update() {
		final Vector<String> colNames = new Vector<String>();
		colNames.add("");
		colNames.add("Name");
		colNames.add("Size");
		colNames.add("##");

		TableUtils.layoutTable(this.jl, this.table, colNames);
	}

	/**
	 * This manages the selections
	 */
	public void valueChanged(final ListSelectionEvent e) {
		if (!e.getValueIsAdjusting()) {
			TableUtils.copyTableSelectionsToJList(this.jl, this.table);

			final int index = this.jl.getSelectedIndex() - 1;

			if ((index < 0) || (this.dirEntry == null) || (this.dirEntry.length < index) || (this.dirEntry[index] == null)) {
			} else { // -------------------- local --------------------------

				final String tgt = this.jl.getSelectedValue().toString();

				for (int i = 0; i < this.dirEntry.length; i++) {
					this.dirEntry[i].setSelected(this.jl.isSelectedIndex(i + 1));
				}
			}
		}
	}


}
