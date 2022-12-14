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
package view;

import controller.config.Settings;
import view.gui.base.AppMenuBar;
import view.gui.base.DownloadList;
import view.gui.base.DownloadQueue;
import view.gui.base.LocalDir;
import view.gui.base.LogFlusher;
import view.gui.base.RemoteDir;
import view.gui.base.StatusPanel;
import view.gui.base.dir.Dir;
import view.gui.base.dir.DirEntry;
import view.gui.framework.FileTransferable;
import view.gui.framework.GUIDefaults;
import view.gui.framework.HDesktopBackground;
import view.gui.framework.HImage;
import view.gui.framework.HImageButton;
import view.gui.hostchooser.HostChooser;
import view.gui.hostchooser.SftpHostChooser;
import view.gui.tasks.HostInfo;
import model.net.BasicConnection;
import model.net.ConnectionHandler;
import model.net.ConnectionListener;
import model.net.FilesystemConnection;
import model.net.FtpConnection;
import model.system.LocalIO;
import model.system.UpdateDaemon;
import model.system.logging.JftpLogger;
import model.system.logging.Log;
import model.system.logging.Log4JLogger;
import controller.tools.RSSFeeder;
import controller.util.I18nHelper;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.DataInputStream;
import java.io.File;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.net.URL;
import java.text.MessageFormat;
import java.util.List;


public class JFtp extends JPanel implements WindowListener, ComponentListener, JftpLogger, ChangeListener,
		InternalFrameListener {
	public static final int CAPACITY = 9;
	public static final int CONNECTION_DATA_LENGTH = 10;
	public static final DownloadList dList = new DownloadList();
	public static final DownloadQueue dQueue = new DownloadQueue();
	public static final HostInfo hostinfo = new HostInfo();
	public static final JDesktopPane desktop = new JDesktopPane();
	private static final int acceptableActions = DnDConstants.ACTION_COPY;
	private static final ConnectionHandler defaultConnectionHandler = new ConnectionHandler();
	private static final java.util.Map<String, javax.swing.JInternalFrame> internalFrames = new java.util.HashMap<>();
	public static final String CENTER = "Center";
	public static final String SOUTH = "South";
	public static StatusPanel statusP;
	public static JLabel statusL = new JLabel(I18nHelper.getUIString("welcome.to.jftp2"));
	public static JFrame mainFrame;
	public static Dir localDir;
	public static Dir remoteDir;
	public static boolean uiBlocked;
	public static JTextArea logTextArea;
	public static AppMenuBar menuBar;
	public static DropTarget dropTarget;
	public static Log4JLogger log4JLogger = new Log4JLogger();
	public static int loginAttempts=0;
	private static boolean mainUsed;
	private static boolean doScroll = true;
	private static DropTargetListener dtListener;
	private static JScrollPane logSp;
    public static boolean isAdmin = false;
	private final JTabbedPane remoteConnectionPanel = new JTabbedPane();
	private final JTabbedPane localConnectionPanel = new JTabbedPane();
	private final boolean initSize = true;
	private final String oldText = "";
	private final JToolBar bottomBar = new JToolBar();
	private final JSplitPane workP = null;
	private final JSplitPane logP = null;
	public RSSFeeder feeder;
	private HostChooser hc;
	private HDesktopBackground background;
	private JInternalFrame j1;
	private JInternalFrame j2;
	private JInternalFrame j3;
	private JInternalFrame j4;
	private JInternalFrame j5;
	private String buffer = "";
	private long oldtime;

	public JFtp() {
		super();
		Log.setLogger(this);

		if (null != statusP) {
			statusP.remove(statusP.close);
		}

		this.init();
		this.displayGUI();
	}

	private JFtp(boolean mainUsed) {
		super();
		Log.setLogger(this);
		JFtp.mainUsed = mainUsed;
		this.init();
		this.displayGUI();
	}

	public static String getHost() {
		return statusP.getHost();
	}

	public static void setHost(String which) {
		statusP.setHost(which);
	}

	public static void localUpdate() {
		localDir.fresh();
	}

	public static void remoteUpdate() {
		remoteDir.fresh();
	}

	public static void safeDisconnect() {
		BasicConnection con = remoteDir.getCon();

		if ((null != con) && con.isConnected()) {
			try {
				con.disconnect();
			} catch (Exception ex) {
			}
		}

		try {
			FilesystemConnection c = new FilesystemConnection();
			c.addConnectionListener((ConnectionListener) remoteDir);
			remoteDir.setCon(c);

			if (!c.chdir("/")) {
				c.chdir("C:\\");
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			log4JLogger.debug(ex.getMessage(), ex);
		}
	}

	private static void setSocksProxyOptions(String proxy, String port) {
		if (proxy.isEmpty() || port.isEmpty()) {
			return;
		}

		java.util.Properties sysprops = System.getProperties();

		sysprops.remove("socksProxyHost");
		sysprops.remove("socksProxyPort");

		sysprops.put("socksProxyHost", proxy);
		sysprops.put("socksProxyPort", port);

		Log.out(MessageFormat.format(I18nHelper.getLogString("socks.proxy.0.1"), sysprops.get("socksProxyHost"),
				sysprops.get("socksProxyPort")));
	}

	public static void main(String[] argv) {
		try {
			long start = System.currentTimeMillis();

			Log.out(I18nHelper.getLogString("starting.up.jftp"));
			System.setProperty("sshtools.logfile", Settings.appHomeDir + "log4.txt");

			Settings.enableResuming = true;
			Settings.enableUploadResuming = true;
			Settings.noUploadResumingQuestion = false;

			setSocksProxyOptions(Settings.getSocksProxyHost(), Settings.getSocksProxyPort());

			JFtp jftp = new JFtp(true);


			if (0 < argv.length) {
				if (argv[0].contains("sftp:")) {
					new SftpHostChooser().update(argv[0]);
				} else {
					jftp.hc.update(argv[0]);
				}
			}

			Log.out(I18nHelper.getLogString("jftp.is.up.and.running"));
			long end = System.currentTimeMillis();
			Log.out(MessageFormat.format(I18nHelper.getLogString("startup.time.0.ms"), end - start));

			//batch processing
			if (1 < argv.length) {
				int idx = 1;
				if (argv[idx].startsWith("localDir=")) {
					String path = argv[idx].substring("localDir=".length());
					Log.debug(MessageFormat.format(I18nHelper.getLogString("setting.local.dir.0"), path));
					localDir.getCon().chdir(path);
					idx++;

					remoteDir.getCon().setLocalPath(localDir.getCon().getPWD());
				}

				while (!remoteDir.getCon().isConnected()) {
					LocalIO.pause(50);
				}

				for (int i = idx; i < argv.length; i++) {
					String path = null;
					String file = argv[i];

					if (argv[i].contains("/")) {
						path = argv[i].substring(0, argv[i].lastIndexOf('/'));
						file = argv[i].substring(argv[i].lastIndexOf('/') + 1);
					}
					Log.debug(MessageFormat.format(I18nHelper.getLogString("download.0.1"), path, file));

					if (null != path) {
						remoteDir.getCon().chdir(path);
					}
					remoteDir.getCon().download(file);
				}
			}
		} catch (Error ex) {
			ex.printStackTrace();
			log4JLogger.debug(ex.getMessage(), ex);
		}
	}

	public static void clearLog() {
		logTextArea.setText("");
		logSp.paintImmediately(0, 0, logSp.getSize().width, logSp.getSize().height);
	}

	private static String getVersion() {
		try {
			URL u = ClassLoader.getSystemResource(Settings.readme);

			if (null == u) {
				u = HImage.class.getResource("/" + Settings.readme);
			}

			DataInputStream i = new DataInputStream(u.openStream());
			String tmp = i.readLine();
			tmp = tmp.substring(tmp.lastIndexOf('>') + 1);
			tmp = tmp.substring(0, tmp.indexOf('<'));

			return tmp;
		} catch (Exception ex) {
		}

		return "";
	}

	public static ConnectionHandler getConnectionHandler() {
		BasicConnection con = remoteDir.getCon();

		if ((null != con) && con instanceof FtpConnection) {
			return ((FtpConnection) con).getConnectionHandler();
		} else {
			return defaultConnectionHandler;
		}
	}

	public static void setAppCursor(Cursor cursor) {

		if (null != mainFrame) {
			if (Cursor.DEFAULT_CURSOR == cursor.getType()) {
				mainFrame.getGlassPane().setCursor(cursor);
				for (int i = 0; i < mainFrame.getGlassPane().getMouseListeners().length; i++) {
					mainFrame.getGlassPane().removeMouseListener(mainFrame.getGlassPane().getMouseListeners()[i]);
				}
				mainFrame.getGlassPane().setVisible(false);
			} else {
				mainFrame.getGlassPane().addMouseListener(new MouseAdapter() {
				});
				mainFrame.getGlassPane().setCursor(cursor);
				mainFrame.getGlassPane().setVisible(true);
			}
		}

	}

	public static void updateMenuBar() {
		menuBar.resetFileItems();
	}

	private void init() {
		dtListener = new DTListener();
		dropTarget = new DropTarget(this, acceptableActions, dtListener, true);

		this.setLayout(new BorderLayout());

		this.setBackground(GUIDefaults.mainBack);
		this.setForeground(GUIDefaults.front);

		statusP = new StatusPanel(this);
		this.add("North", statusP);

		localDir = new LocalDir(Settings.defaultWorkDir);
		localDir.setDownloadList(dList);

		remoteDir = new RemoteDir();
		remoteDir.setDownloadList(dList);

		Dimension d = Settings.getWindowSize();
		this.setPreferredSize(d);
		this.setSize(d);

		int width = (int) d.getWidth();
		int height = (int) d.getHeight();

		dList.setMinimumSize(new Dimension((int) (width / 2.2), (int) (height * 0.20)));
		dList.setPreferredSize(new Dimension((int) (width / 2.2), (int) (height * 0.25)));
		dList.setSize(new Dimension((int) (width / 2.2), (int) (height * 0.25)));

		desktop.setDragMode(JDesktopPane.OUTLINE_DRAG_MODE);
		this.addBackgroundImage();

		this.j1 = new JInternalFrame(I18nHelper.getUIString("local.filesystem"), true, false, true, true);
		this.j1.setMinimumSize(new Dimension(300, 300));
		this.j1.setLocation(5, 5);
		this.localConnectionPanel.addTab("file://", null, (Component) localDir, I18nHelper.getUIString("filesystem"));
		this.localConnectionPanel.setSelectedIndex(0);
		this.localConnectionPanel.addChangeListener(this);
		this.j1.getContentPane().add(this.localConnectionPanel);
		localDir.fresh();
		desktop.add(this.j1);
		this.j1.setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
		this.j1.addInternalFrameListener(this);
		this.j1.pack();
		this.j1.setSize(new Dimension(460, 480));
		this.j1.show();

		this.j2 = new JInternalFrame(I18nHelper.getUIString("remote.connections"), true, false, true, true);
		this.j2.setLocation(470, 5);
		this.remoteConnectionPanel.addTab("file://", null, (Component) remoteDir, I18nHelper.getUIString("filesystem"
		));
		this.remoteConnectionPanel.setSelectedIndex(0);
		this.remoteConnectionPanel.addChangeListener(this);
		this.j2.getContentPane().add(this.remoteConnectionPanel);
		desktop.add(this.j2);
		this.j2.setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
		this.j2.addInternalFrameListener(this);
		this.j2.pack();
		this.j2.setSize(new Dimension(460, this.j1.getSize().height));
		this.j2.show();

		logTextArea = new JTextArea();
		logTextArea.setBackground(GUIDefaults.light);
		logTextArea.setForeground(GUIDefaults.front);
		logTextArea.setEditable(false);
		logSp = new JScrollPane(logTextArea);
		logSp.setSize(new Dimension(428, 148));

		this.j5 = new JInternalFrame(I18nHelper.getUIString("queue.system"), true, false, true, true);
		this.j5.setLocation(5, 500);
		this.j5.getContentPane().add(dQueue, BorderLayout.CENTER);
		desktop.add(this.j5);
		this.j5.pack();
		this.j5.setSize(new Dimension(440, 150));
		this.j5.show();

		this.j3 = new JInternalFrame(I18nHelper.getUIString("log2"), true, false, true, true);

		HImageButton clearButton = new HImageButton(Settings.clearLogImage, "clearLog", I18nHelper.getUIString("clear"
				+ ".log1"), e -> clearLog());
		HImageButton lockButton = new HImageButton(Settings.scrollLockImage, "scrollLock", I18nHelper.getUIString(
				"toggle.scroll.lock"), e -> {
			doScroll = !doScroll;

			statusP.status(MessageFormat.format(I18nHelper.getUIString("scroll.lock.0.choice.0.deactivated.1"
					+ ".activated"), doScroll ? 0 : 1));
		});

		JPanel logSpPanel = new JPanel();
		JPanel logButtonPanel = new JPanel();
		logButtonPanel.setPreferredSize(new Dimension(18, 100));
		logButtonPanel.add(clearButton);
		logButtonPanel.add(lockButton);
		logSpPanel.setLayout(new BorderLayout(2, 2));
		logSpPanel.add("West", logButtonPanel);
		logSpPanel.add(CENTER, logSp);

		int x = desktop.getSize().width / 2;
		this.j3.setLocation(5, 525);
		this.j3.getContentPane().add(logSpPanel, BorderLayout.CENTER);
		desktop.add(this.j3);
		this.j3.pack();
		this.j3.setSize(new Dimension(440, 150));
		this.j3.show();

		this.j4 = new JInternalFrame(I18nHelper.getLogString("download.manager"), true, false, true, true);
		this.j4.setLocation(450, 500);
		this.j4.getContentPane().add(dList, BorderLayout.CENTER);
		desktop.add(this.j4);
		this.j4.pack();
		this.j4.setSize(new Dimension(480, 175));
		this.j4.show();

		this.j1.toFront();
		this.j2.toFront();

		this.add(CENTER, desktop);

		this.bottomBar.setFloatable(false);
		this.bottomBar.add(StatusPanel.status, FlowLayout.LEFT);

		if (Settings.getEnableRSS()) {
			this.addRSS();
		}

		this.add(SOUTH, this.bottomBar);

		this.addComponentListener(this);
		this.componentResized(new ComponentEvent(logTextArea, 0));

		this.validate();
		this.setVisible(true);

		if (mainUsed) {
			this.chooseHost();
		} else {
			SwingUtilities.invokeLater(this::chooseHost);
		}

		LogFlusher flusher = new LogFlusher();
		UpdateDaemon daemon = new UpdateDaemon(this);

	}

	public void addRSS() {
		this.feeder = new RSSFeeder();
		this.bottomBar.add(this.feeder);
		this.bottomBar.validate();
	}

	private void chooseHost() {
		this.hc = new HostChooser(this);

		if (!mainUsed) {
			this.hc.update();
		}
	}

	private void saveInternalPositions() {
		this.saveInternalPosition(this.j1, "local");
		this.saveInternalPosition(this.j2, "remote");
		this.saveInternalPosition(this.j5, "queue");
		this.saveInternalPosition(this.j3, "log");
		this.saveInternalPosition(this.j4, "manager");
	}

	private void restoreInternalPositions() {
		if (Settings.getProperty("jftp.iframes.resize").equals("false")) {

		} else {
			this.restoreInternalPosition(this.j1, "local");
			this.restoreInternalPosition(this.j2, "remote");
			this.restoreInternalPosition(this.j5, "queue");
			this.restoreInternalPosition(this.j3, "log");
			this.restoreInternalPosition(this.j4, "manager");
		}
	}

	private void restoreInternalPosition(JInternalFrame f, String desc) {
		String x = Settings.getProperty("jftp.iframes." + desc + ".x");
		String y = Settings.getProperty("jftp.iframes." + desc + ".y");
		String w = Settings.getProperty("jftp.iframes." + desc + ".width");
		String h = Settings.getProperty("jftp.iframes." + desc + ".height");

		if (x.contains(".")) {
			x = x.substring(0, x.indexOf('.'));
		}
		if (y.contains(".")) {
			y = y.substring(0, y.indexOf('.'));
		}

		try {
			f.setLocation(Integer.parseInt(x), Integer.parseInt(y));
			f.setSize(Integer.parseInt(w), Integer.parseInt(h));
		} catch (Exception ex) {
			Log.out(MessageFormat.format(I18nHelper.getLogString("can.not.set.internal.fram.position.for.0"), desc));
		}
	}

	private void saveInternalPosition(JInternalFrame f, String desc) {
		Point p = f.getLocation();

		Settings.setProperty("jftp.iframes." + desc + ".x", String.valueOf(p.getX()));
		Settings.setProperty("jftp.iframes." + desc + ".y", String.valueOf(p.getY()));
		Settings.setProperty("jftp.iframes." + desc + ".width", f.getWidth());
		Settings.setProperty("jftp.iframes." + desc + ".height", f.getHeight());
	}

	public void windowClosing(WindowEvent e) {
		this.saveInternalPositions();

		Settings.setProperty("jftp.window.width", this.getWidth());
		Settings.setProperty("jftp.window.height", this.getHeight());

		if (mainUsed) {
			Settings.setProperty("jftp.window.x", (int) mainFrame.getLocationOnScreen().getX());
			Settings.setProperty("jftp.window.y", (int) mainFrame.getLocationOnScreen().getY());
		} else {
			Settings.setProperty("jftp.window.x", (int) this.getLocationOnScreen().getX());
			Settings.setProperty("jftp.window.y", (int) this.getLocationOnScreen().getY());
		}

		Settings.save();
		safeDisconnect();

		if (Settings.isStandalone) {
			System.exit(0);
		} else {
			mainFrame.dispose();
		}
	}

	public void windowClosed(WindowEvent e) {
	}

	public void windowActivated(WindowEvent e) {
	}

	public void windowDeactivated(WindowEvent e) {
	}

	public void windowIconified(WindowEvent e) {
	}

	public void windowDeiconified(WindowEvent e) {
	}

	public void windowOpened(WindowEvent e) {
	}

	public void componentHidden(ComponentEvent e) {
	}

	public void componentMoved(ComponentEvent e) {
	}

	public void componentShown(ComponentEvent e) {
	}

	public void componentResized(ComponentEvent e) {
		localDir.actionPerformed(this, "local");
		remoteDir.actionPerformed(this, "remote");
		desktop.remove(this.background);
		this.addBackgroundImage();

		this.validate();

		System.out.println(StatusPanel.status);
		StatusPanel.status.fresh();
	}

	private void addBackgroundImage() {
		try {
			this.background = new HDesktopBackground(Settings.background, null);
			this.background.setBounds(0, 0, this.getSize().width, this.getSize().height);
			desktop.add(this.background, Integer.MIN_VALUE);
		} catch (Exception ex) {
			Log.out(MessageFormat.format(I18nHelper.getLogString("0.missing.no.background.image.used"),
					Settings.background));
		}
	}

	private void displayGUI() {
		UIManager.getLookAndFeelDefaults().put("ClassLoader", this.getClass().getClassLoader());

		String tmp = Settings.getLookAndFeel();
		if (null != tmp) {
			this.setLookAndFeel(Settings.getLookAndFeel());
		} else {
			this.setLookAndFeel("net.sourceforge.mlf.metouia.MetouiaLookAndFeel");
		}

		if ((null == Settings.getLookAndFeel())
				|| !Settings.getLookAndFeel().equals("com.incors.plaf.kunststoff.KunststoffLookAndFeel")) {
			try {
				Class.forName("com.incors.plaf.kunststoff.KunststoffLookAndFeel");
				UIManager.installLookAndFeel("Kunststoff", "com.incors.plaf.kunststoff.KunststoffLookAndFeel");
			} catch (ClassNotFoundException cnfe) {
			}
		}

		if ((null == Settings.getLookAndFeel())
				|| !Settings.getLookAndFeel().equals("net.sourceforge.mlf.metouia.MetouiaLookAndFeel")) {

			try {
				Class.forName("net.sourceforge.mlf.metouia.MetouiaLookAndFeel");
				UIManager.installLookAndFeel("Metouia", "net.sourceforge.mlf.metouia.MetouiaLookAndFeel");
			} catch (ClassNotFoundException cnfe) {
			}
		}

		mainFrame = new JFrame();
		mainFrame.setLocation(Settings.getWindowLocation());

		mainFrame.setTitle(MessageFormat.format(I18nHelper.getUIString("0.version.1"), Settings.title, getVersion()));

		mainFrame.setResizable(Settings.resize);
		mainFrame.addWindowListener(this);

		Image icon = HImage.getImage(this, Settings.iconImage);
		mainFrame.setIconImage(icon);
		mainFrame.setFont(GUIDefaults.font);

		menuBar = new AppMenuBar(this);
		mainFrame.setJMenuBar(menuBar);

		mainFrame.getContentPane().setLayout(new BorderLayout());
		mainFrame.getContentPane().add(CENTER, this);
		SwingUtilities.updateComponentTreeUI(mainFrame);
		mainFrame.pack();
		mainFrame.validate();
		mainFrame.setVisible(true);
		JOptionPane.showMessageDialog(mainFrame, I18nHelper.getUIString("welcome.to.jftp"));
	}

	private void log(String msg) {
		if (msg.startsWith("200") || msg.startsWith("227")) {
			if ((0 < msg.indexOf("NOOP")) || (0 < msg.indexOf("Type")) || (0 < msg.indexOf("MODE")) || (0
					< msg.indexOf("Passive"))) {
				if (Settings.hideStatus) {
					return;
				}
			}
		} else if (null == logTextArea) {
			return;
		}

		if (!msg.isEmpty()) {
			this.buffer = this.buffer + " " + msg;
		}

		logTextArea.append(this.buffer);
		log4JLogger.debug(this.buffer);
		this.buffer = "";

		long time = System.currentTimeMillis();

		if ((Settings.uiRefresh > (time - this.oldtime))) {
			UpdateDaemon.updateLog();

			return;
		}

		this.oldtime = time;

		if (doScroll) {
			JScrollBar bar = logSp.getVerticalScrollBar();
			bar.setValue(bar.getMaximum());
		}

		this.repaint();
		this.revalidate();
	}

	private void logRaw(String msg) {
		logTextArea.append(" " + msg);
		log4JLogger.debug(msg);
		Log.out(I18nHelper.getLogString("note.lograw.called"));
		this.paintImmediately(0, 0, this.getSize().width, this.getSize().height);

	}

	private void log(String msg, Throwable throwable) {
		PrintWriter p = new PrintWriter(new StringWriter());
		throwable.printStackTrace(p);
		this.log(msg);
		this.log(p.toString());
	}

	public void debug(String msg) {
		this.log(msg + "\n");
	}

	public void debugRaw(String msg) {
		this.logRaw(msg);
	}

	public void debug(String msg, Throwable throwable) {
		this.log(msg, throwable);
	}

	public void warn(String msg) {
		this.log(msg);
	}

	public void warn(String msg, Throwable throwable) {
		this.log(msg, throwable);
	}

	public void error(String msg) {
		this.log(msg);
	}

	public void error(String msg, Throwable throwable) {
		this.log(msg, throwable);
	}

	public void info(String msg) {
		this.log(msg);
	}

	public void info(String msg, Throwable throwable) {
		this.log(msg, throwable);
	}

	public void fatal(String msg) {
		this.log(msg);
	}

	public void fatal(String msg, Throwable throwable) {
		this.log(msg, throwable);
	}

	public void fireUpdate() {
		LocalIO.pause(200);
		this.repaint();
	}

	public void ensureLogging() {
		if (this.buffer.isEmpty()) {
			JScrollBar bar;

			if ((null == logSp) || (null == (bar = logSp.getVerticalScrollBar())) || null == bar || (bar.getValue()
					== bar.getMaximum()) || bar.getValueIsAdjusting()) {
				return;
			} else {
				if (doScroll) {
					bar.setValue(bar.getMaximum());
				}

				logSp.repaint();

				this.repaint();
				this.revalidate();
			}

			return;
		}

		Log.out(I18nHelper.getLogString("flushing.log.buffer"));
		this.oldtime = 0;
		this.log("");
	}

	public void setLookAndFeel(String name) {
		if (null == name) {
			return;
		}

		try {
			UIManager.setLookAndFeel(name);

			if (null != mainFrame) {
				SwingUtilities.invokeLater(() -> {
					javax.swing.SwingUtilities.updateComponentTreeUI(mainFrame);
					javax.swing.SwingUtilities.updateComponentTreeUI(statusP);
				});
			}
		} catch (Exception ex) {
			Log.debug(MessageFormat.format(I18nHelper.getLogString("error.setting.look.and.feel.0"), ex));
		}
	}

	public void addConnection(String name, BasicConnection con) {
		con.addConnectionListener((ConnectionListener) localDir);

		Dir tmp = new RemoteDir();
		tmp.setDownloadList(dList);
		con.addConnectionListener((ConnectionListener) tmp);
		tmp.setCon(con);

		int x = this.remoteConnectionPanel.getSelectedIndex();
		this.remoteConnectionPanel.addTab(this.parse(name), null, (Component) tmp,
				MessageFormat.format(I18nHelper.getUIString("switch.to.02"), this.parse(name)));
		this.remoteConnectionPanel.setSelectedIndex(x + 1);
		this.j2.setClosable(true);
	}

	public void addLocalConnection(String name, BasicConnection con) {
		con.addConnectionListener((ConnectionListener) remoteDir);

		Dir tmp = new LocalDir();
		tmp.setDownloadList(dList);
		con.addConnectionListener((ConnectionListener) tmp);
		tmp.setCon(con);

		int x = this.localConnectionPanel.getSelectedIndex();
		this.localConnectionPanel.addTab(this.parse(name), null, (Component) tmp,
				MessageFormat.format(I18nHelper.getUIString("switch.to.0"), this.parse(name)));
		this.localConnectionPanel.setSelectedIndex(x + 1);
		this.j1.setClosable(true);
	}

	private String parse(String what) {
		if (what.contains("@")) {
			return what.substring(what.lastIndexOf('@') + 1);
		} else {
			return what;
		}
	}

	public void stateChanged(ChangeEvent e) {
		remoteDir = (Dir) this.remoteConnectionPanel.getSelectedComponent();
		localDir = (Dir) this.localConnectionPanel.getSelectedComponent();
		remoteDir.getCon().setLocalPath(localDir.getPath());

	}

	public void closeCurrentTab() {
		int x = this.remoteConnectionPanel.getSelectedIndex();

		if (0 < x) {
			safeDisconnect();
			this.remoteConnectionPanel.remove(x);
			this.remoteConnectionPanel.setSelectedIndex(x - 1);
		}

		if (2 > this.remoteConnectionPanel.getTabCount()) {
			this.j2.setClosable(false);
		}
	}

	public void closeCurrentLocalTab() {
		int x = this.localConnectionPanel.getSelectedIndex();

		if (0 < x) {
			BasicConnection con = localDir.getCon();

			if ((null != con) && con.isConnected()) {
				try {
					con.disconnect();
				} catch (Exception ex) {
				}
			}

			this.localConnectionPanel.remove(x);
			this.localConnectionPanel.setSelectedIndex(x - 1);
		}

		if (2 > this.localConnectionPanel.getTabCount()) {
			this.j1.setClosable(false);
		}
	}

	public void addToDesktop(String title, Component c, int w, int h) {
		JInternalFrame jt = new JInternalFrame(title, false, true, false, true);

		if (500 > w) {
			jt.setLocation(200, 100);
		} else {
			jt.setLocation(80, 100);
		}

		jt.getContentPane().add(c);
		desktop.add(jt);

		internalFrames.put(String.valueOf(c.hashCode()), jt);

		jt.pack();
		jt.setSize(new Dimension(w, h));
		jt.show();
	}

	public void removeFromDesktop(int component) {
		JInternalFrame f = internalFrames.get(String.valueOf(component));

		if (null != f) {
			f.dispose();

		} else {
			Log.debug(MessageFormat.format(I18nHelper.getLogString("error.0.not.found.in.hashtable3"), component));
		}
	}

	public void setClosable(int component, boolean ok) {
		JInternalFrame f = internalFrames.get(String.valueOf(component));

		if (null != f) {
			f.setClosable(ok);
		} else {
			Log.debug(MessageFormat.format(I18nHelper.getLogString("error.0.not.found.in.hashtable2"), component));
		}
	}

	public void setLocation(int component, int x, int y) {
		JInternalFrame f = internalFrames.get(String.valueOf(component));

		if (null != f) {
			f.setLocation(x, y);
		} else {
			Log.debug(MessageFormat.format(I18nHelper.getLogString("error.0.not.found.in.hashtable"), component));
		}
	}

	public void internalFrameClosing(InternalFrameEvent e) {
		if (e.getSource() == this.j1) {
			this.closeCurrentLocalTab();
		} else if (e.getSource() == this.j2) {
			this.closeCurrentTab();
		}
	}

	public void internalFrameActivated(InternalFrameEvent e) {
	}

	public void internalFrameClosed(InternalFrameEvent e) {
	}

	public void internalFrameDeactivated(InternalFrameEvent e) {
	}

	public void internalFrameDeiconified(InternalFrameEvent e) {
	}

	public void internalFrameIconified(InternalFrameEvent e) {
	}

	public void internalFrameOpened(InternalFrameEvent e) {
	}

	public void drop() {
		try {
			this.handleDrop(null, Toolkit.getDefaultToolkit().getSystemClipboard().getContents(this));
		} catch (Exception ex) {
			ex.printStackTrace();
			log4JLogger.debug(ex.getMessage(), ex);
		}
	}

	private void handleDrop(DropTargetDropEvent e, Transferable t) throws java.awt.datatransfer.UnsupportedFlavorException, java.io.IOException {
		System.out.println(I18nHelper.getLogString("starting.dropattempt"));

		DataFlavor chosen = DataFlavor.javaFileListFlavor;
		DataFlavor second = FileTransferable.plainTextFlavor;
		DataFlavor flavor = null;
		Object data = null;

		if (null != e) {
			flavor = e.getCurrentDataFlavors()[0];
			e.acceptDrop(acceptableActions);

			Class<?> c = flavor.getDefaultRepresentationClass();
		}

		if (null == flavor) {
			flavor = second;
		}

		String name = "";

		if (t.isDataFlavorSupported(chosen)) {
			System.out.println(I18nHelper.getLogString("using.list.dnd.style"));

			List myList = (java.util.List) t.getTransferData(chosen);

			File[] f = (File[]) myList.toArray();

			for (java.io.File file : f) {
				name = file.getAbsolutePath();
				System.out.println(MessageFormat.format(I18nHelper.getLogString("dnd.file.02"), name));

				name = name.replace("\r", "");
				name = name.replace("\n", "");
			}

			this.draggedTransfer(f, name);
		} else if (t.isDataFlavorSupported(second)) {
			System.out.println(I18nHelper.getLogString("using.text.plain.dnd.style"));

			data = t.getTransferData(flavor);

			StringBuilder str = new StringBuilder();
			int i = 0;

			if (data instanceof Reader) {
				int c;

				while (-1 != (c = ((java.io.Reader) data).read())) {
					if (((1 == i) && (0 == c))) {
						i = -1;
					} else {
						str.append((char) c);
					}

					i++;
				}
			} else {
				str = new StringBuilder(String.valueOf(data));
			}

			System.out.println(MessageFormat.format(I18nHelper.getLogString("object.data.0"), str));

			if (str.toString().startsWith("<")) {
				Log.debug(I18nHelper.getLogString("mozilla.dnd.detected.preparsing"));
				str = new StringBuilder(str.substring(str.indexOf("\"") + 1));
				str = new StringBuilder(str.substring(0, str.indexOf("\"")));
				Log.debug(MessageFormat.format(I18nHelper.getLogString("parsed.data.0"), str));
			}

			if (str.toString().contains("[")) {
				Log.debug(I18nHelper.getLogString("windows.dnd.detected"));
				name = str.substring(str.indexOf("[") + 1);
				name = name.substring(0, name.lastIndexOf(']'));
			} else if (str.toString().startsWith("file://")) {
				name = str.substring(7);

				name = name.replace("\r", "");
				name = name.replace("\n", "");
				Log.debug(MessageFormat.format(I18nHelper.getLogString("file.url.dnd.detected.0"), name));
			}

			if (!new File(name).exists()) {
				System.out.println(MessageFormat.format(I18nHelper.getLogString("no.file.string.in.clipboard.0"),
						name));

				return;
			}

			System.out.println(MessageFormat.format(I18nHelper.getLogString("dnd.file.0"), name));

			File[] f1 = new File[1];
			f1[0] = new File(name);

			this.draggedTransfer(f1, name);
		}
	}

	private void draggedTransfer(File[] f, String name) {
		if ((1 == f.length) && f[0].isFile()) {
			String path = "";

			if (name.contains("/")) {
				path = name.substring(0, name.lastIndexOf('/') + 1);
				name = name.substring(name.lastIndexOf('/') + 1);
			}

			Log.debug(MessageFormat.format(I18nHelper.getLogString("dnd.0.1"), path, name));

			if (!path.trim().isEmpty()) {
				((LocalDir) localDir).chdir(path);
			}

			((LocalDir) localDir).startTransfer(new DirEntry(name, ((ActionListener) localDir)));
		} else {
			Log.debug(I18nHelper.getLogString("dragging.multiple.files.or.dirs.is.not.yet.supported"));
		}
	}

	class DTListener extends DropTargetAdapter {
		public void dragEnter(DropTargetDragEvent e) {
			e.acceptDrag(acceptableActions);
		}

		public void dragOver(DropTargetDragEvent e) {
			e.acceptDrag(acceptableActions);
		}

		public void dropActionChanged(DropTargetDragEvent e) {
			e.acceptDrag(acceptableActions);
		}

		public void drop(DropTargetDropEvent e) {
			try {
				JFtp.this.handleDrop(e, e.getTransferable());

				e.dropComplete(true);
				UpdateDaemon.updateRemoteDir();
			} catch (Throwable t) {
				t.printStackTrace();
				e.dropComplete(false);

			}
		}
	}
}
