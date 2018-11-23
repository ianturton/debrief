/*
 *    Debrief - the Open Source Maritime Analysis Application
 *    http://debrief.info
 *
 *    (C) 2000-2018, Deep Blue C Technology Ltd
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the Eclipse Public License v1.0
 *    (http://www.eclipse.org/legal/epl-v10.html)
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */
package Debrief.GUI.Lite;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import Debrief.GUI.Lite.custom.JPanelWithTitleBar;
import MWC.GUI.Toolbar;

/**
 * @author Ayesha <ayesha.ma@gmail.com>
 *
 */
public class DebriefLiteApp
{

  public static final String appName="Debrief Lite";
  private JFrame theFrame;
  Toolbar _theToolbar;
  private JMenuBar theMenuBar;
  private JMenu theMenu;
  private static JLabel statusBar;
  
  private JPanelWithTitleBar _timeControllerPanel,_outlinePanel,_graphPanel,_editorPanel;
  public DebriefLiteApp()
  {
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

    } catch (ClassNotFoundException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    } catch (InstantiationException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    } catch (IllegalAccessException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    } catch (UnsupportedLookAndFeelException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    }
    theFrame = new JFrame(appName);
    initForm();
    createAppPanels();
    
    theFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    theFrame.setVisible(true);
    
  }

  public static void main(String[] args) {
    new DebriefLiteApp();
  }
  
  private void createAppPanels()
  {
    final Dimension frameSize = theFrame.getSize();
    final int width = (int)frameSize.getWidth();
    final int height = (int)frameSize.getHeight();
    _timeControllerPanel = new JPanelWithTitleBar("Time Controller");
    _outlinePanel = new JPanelWithTitleBar("Outline");
    _editorPanel = new JPanelWithTitleBar("Plot Editor");
    _graphPanel = new JPanelWithTitleBar("Graph");
    final JScrollPane timeControllerPane = createScrollPane(_timeControllerPanel);
    final JScrollPane outlinePane = createScrollPane(_outlinePanel);
    final JScrollPane editorPane = createScrollPane(_editorPanel);
    final JScrollPane graphPane = createScrollPane(_graphPanel);
    final JSplitPane splitPane1 = new JSplitPane(JSplitPane.VERTICAL_SPLIT,true,timeControllerPane,outlinePane);
    final JSplitPane splitPane2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT,true,editorPane,graphPane);
    final JSplitPane splitPane3 = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,splitPane1,splitPane2);
    final JSplitPane splitPane4 = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,splitPane3,new JPanel());
    splitPane4.setOneTouchExpandable(true);
    
    splitPane1.setOneTouchExpandable(true);
    splitPane2.setOneTouchExpandable(true);
    splitPane4.setDividerLocation(width-50);
    splitPane2.setDividerLocation(height/2+height/5);
    splitPane1.setDividerLocation(height/2);
    splitPane3.setDividerLocation(width/3);
    splitPane4.setResizeWeight(0.9);
    splitPane2.setResizeWeight(0.5);
    _timeControllerPanel.addMinMaxListenerFor(splitPane1,true);
    _outlinePanel.addMinMaxListenerFor(splitPane1,false);
    _editorPanel.addMinMaxListenerFor(splitPane2,true);
    _graphPanel.addMinMaxListenerFor(splitPane2,false);
    splitPane3.setOneTouchExpandable(true);
    theFrame.add(splitPane4,BorderLayout.CENTER);
    addStatusBar();
  }
  
  
  public static void setStatus(String message) {
    statusBar.setText(message);
  }
  
  private void addStatusBar() {
    statusBar = new JLabel("Status bar for displaying statuses");
    theFrame.add(statusBar, BorderLayout.SOUTH);    
  }
  private JScrollPane createScrollPane(final JPanelWithTitleBar jTitleBar) {
    JPanel panel1 = new JPanel();
    panel1.setLayout(new BorderLayout());
    panel1.add(jTitleBar,BorderLayout.NORTH);
    JScrollPane scrPane1 = new JScrollPane(panel1);
    return scrPane1;
  }
  
  
  /**
   * fill in the UI details
   */
  private void initForm()
  {

    theFrame = new JFrame(appName + " (" + Debrief.GUI.VersionInfo.getVersion() + ")");


    theFrame.addWindowListener(new WindowAdapter()
    {
      public void windowClosing(final java.awt.event.WindowEvent e)
      {
        System.exit(0);
      }
    });

    // try to give the application an icon
    final java.net.URL iconURL = getClass().getClassLoader().getResource("images/icon.png");
    if (iconURL != null)
    {
      final ImageIcon myIcon = new ImageIcon(iconURL);
      if (myIcon != null)
        theFrame.setIconImage(myIcon.getImage());
    }
    // create the components
    final MWC.GUI.Tools.Swing.SwingToolbar theToolbar =
      new MWC.GUI.Tools.Swing.SwingToolbar(Toolbar.HORIZONTAL, "Application", null);

    // pass the toolbar back to the parent
    setToolbar(theToolbar);

    // and the panel
    final JPanel topSection = new JPanel();
    topSection.setLayout(new BorderLayout());
    theMenuBar = new JMenuBar();
    theFrame.setJMenuBar(theMenuBar);

    // add them
    theFrame.getContentPane().add("North", theToolbar);

    // tidy up

    final Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();

    theFrame.setSize((int) (dim.width * 0.6),
                     (int) (dim.height * 0.6));
    final Dimension sz = theFrame.getSize();
    theFrame.setLocation((dim.width - sz.width) / 2,
                         (dim.height - sz.height) / 2);

   
    // do any final re-arranging
    theFrame.doLayout();

  }

  /**
   * @param theBar
   *          autofilled
   */
  protected final void setToolbar(final Toolbar theBar)
  {
    // store this locally
    _theToolbar = theBar;

  }
  
}
