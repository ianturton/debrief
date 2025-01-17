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
package org.mwc.debrief.lite.menu;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.mwc.debrief.lite.DebriefLiteApp;
import org.mwc.debrief.lite.gui.LiteStepControl;
import org.mwc.debrief.lite.gui.LiteStepControl.SliderControls;
import org.mwc.debrief.lite.gui.LiteStepControl.TimeLabel;
import org.mwc.debrief.lite.gui.custom.RangeSlider;
import org.mwc.debrief.lite.map.GeoToolMapRenderer;
import org.mwc.debrief.lite.properties.PropertiesDialog;
import org.pushingpixels.flamingo.api.common.CommandButtonDisplayState;
import org.pushingpixels.flamingo.api.common.FlamingoCommand.FlamingoCommandToggleGroup;
import org.pushingpixels.flamingo.api.common.JCommandButton;
import org.pushingpixels.flamingo.api.common.RichTooltip;
import org.pushingpixels.flamingo.api.common.RichTooltip.RichTooltipBuilder;
import org.pushingpixels.flamingo.api.common.icon.ImageWrapperResizableIcon;
import org.pushingpixels.flamingo.api.ribbon.JRibbon;
import org.pushingpixels.flamingo.api.ribbon.JRibbonBand;
import org.pushingpixels.flamingo.api.ribbon.JRibbonComponent;
import org.pushingpixels.flamingo.api.ribbon.RibbonElementPriority;
import org.pushingpixels.flamingo.api.ribbon.RibbonTask;

import Debrief.Wrappers.TrackWrapper;
import MWC.GUI.CanvasType;
import MWC.GUI.Editable;
import MWC.GUI.Layer;
import MWC.GUI.Layers;
import MWC.GUI.StepperListener;
import MWC.GUI.ToolParent;
import MWC.GUI.Layers.DataListener;
import MWC.GUI.Tools.Swing.MyMetalToolBarUI.ToolbarOwner;
import MWC.GUI.Undo.UndoBuffer;
import MWC.GenericData.HiResDate;
import MWC.GenericData.TimePeriod;
import MWC.TacticalData.SliderConverter;
import MWC.TacticalData.temporal.ControllablePeriod;
import MWC.TacticalData.temporal.PlotOperations;
import MWC.TacticalData.temporal.TimeManager;

public class DebriefRibbonTimeController
{

  /**
   * Class that binds the Time Filter and Time Label. It is used to update the date formatting.
   *
   */
  protected static class DateFormatBinder
  {
    protected LiteStepControl stepControl;
    protected JLabel minimumValue;
    protected JLabel maximumValue;
    protected RangeSlider slider;
    protected TimeManager timeManager;

    public String getDateFormat()
    {
      return stepControl.getDateFormat();
    }

    public void updateFilterDateFormat()
    {
      final Date low = RangeSlider.toDate(slider.getValue()).getTime();
      final Date high = RangeSlider.toDate(slider.getUpperValue()).getTime();

      final SimpleDateFormat formatter = new SimpleDateFormat(stepControl
          .getDateFormat());
      formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
      minimumValue.setText(formatter.format(low));
      maximumValue.setText(formatter.format(high));
    }

    public void updateTimeDateFormat(final String format,
        final boolean updateTimeLabel, final boolean updateFilters)
    {
      if (updateTimeLabel)
      {
        stepControl.setDateFormat(format);
      }
      if (updateFilters)
      {
        updateFilterDateFormat();
      }
    }

    public void reset()
    {
      minimumValue.setText(" ");
      maximumValue.setText(" ");
    }
  }

  private static class LiteSliderControls implements SliderControls
  {
    private final RangeSlider slider;

    private LiteSliderControls(final RangeSlider slider)
    {
      this.slider = slider;
    }

    @Override
    public HiResDate getToolboxEndTime()
    {
      final long val = slider.getUpperDate().getTimeInMillis();
      return new HiResDate(val);
    }

    @Override
    public HiResDate getToolboxStartTime()
    {
      final long val = slider.getLowerDate().getTimeInMillis();
      return new HiResDate(val);
    }

    @Override
    public void setEnabled(final boolean enabled)
    {
      slider.setEnabled(enabled);
    }

    @Override
    public void setToolboxEndTime(final HiResDate val)
    {
      final GregorianCalendar cal = new GregorianCalendar();
      cal.setTimeInMillis(val.getDate().getTime());
      slider.setMaximum(cal);
      slider.setUpperDate(cal);
    }

    @Override
    public void setToolboxStartTime(final HiResDate val)
    {
      final GregorianCalendar cal = new GregorianCalendar();
      cal.setTimeInMillis(val.getDate().getTime());
      slider.setMinimum(cal);
      slider.setLowerDate(cal);
    }
  }

  private static abstract class LiteStepperListener implements StepperListener
  {
    private final JCommandButton _playBtn;

    private LiteStepperListener(final JCommandButton playCommandButton)
    {
      _playBtn = playCommandButton;
    }

    @Override
    public void newTime(final HiResDate oldDTG, final HiResDate newDTG,
        final CanvasType canvas)
    {
      // ignore
    }

    @Override
    public void steppingModeChanged(final boolean on)
    {
      if (_playBtn != null)
      {
        updatePlayBtnUI(_playBtn, !on);
      }
    }
  }

  protected static class ShowFormatAction extends AbstractAction
  {
    /**
     *
     */
    private static final long serialVersionUID = 1L;
    private final JPopupMenu menu;

    private ShowFormatAction(final JPopupMenu theMenu)
    {
      this.menu = theMenu;
    }

    @Override
    public void actionPerformed(final ActionEvent e)
    {
      // Get the event source
      final Component component = (Component) e.getSource();

      menu.show(component, 0, 0);

      // Get the location of the point 'on the screen'
      final Point p = component.getLocationOnScreen();

      menu.setLocation(p.x, p.y + component.getHeight());
    }
  }

  private static class SliderListener implements ChangeListener
  {
    final private PlotOperations operations;
    final private TimeManager timeManager;

    private SliderListener(final PlotOperations operations,
        final TimeManager time)
    {
      this.operations = operations;
      timeManager = time;
    }

    @Override
    public void stateChanged(final ChangeEvent e)
    {
      final RangeSlider slider = (RangeSlider) e.getSource();

      final Date low = RangeSlider.toDate(slider.getValue()).getTime();
      final Date high = RangeSlider.toDate(slider.getUpperValue()).getTime();
      formatBinder.updateFilterDateFormat();

      operations.setPeriod(new TimePeriod.BaseTimePeriod(new HiResDate(low),
          new HiResDate(high)));

      final HiResDate currentTime = timeManager.getTime();
      if (currentTime != null)
      {
        Date oldTime = currentTime.getDate();
        if (oldTime.before(low))
        {
          oldTime = low;
        }
        if (oldTime.after(high))
        {
          oldTime = high;
        }
        label.setRange(low.getTime(), high.getTime());
        label.setValue(oldTime.getTime());

        // and enable those buttons
      }

      operations.performOperation(ControllablePeriod.FILTER_TO_TIME_PERIOD);
    }
  }

  private static final String START_TEXT = "Start playing";

  private static final String STOP_TEXT = "Stop playing";

  private static final String STOP_IMAGE = "icons/24/media_stop.png";

  private static final String PLAY_IMAGE = "icons/24/media_play.png";

  public static JPanel topButtonsPanel;
  private static final String[] timeFormats = new String[]
  {"mm:ss.SSS", "HHmm.ss", "HHmm", "ddHHmm", "ddHHmm:ss", "yy/MM/dd HH:mm",
      "yy/MM/dd hh:mm:ss"};

  private static SliderConverter converter = new SliderConverter();

  private static DateFormatBinder formatBinder = new DateFormatBinder();

  private static TimeLabel label;

  private static JCheckBoxMenuItem[] _menuItem;

  protected static void addTimeControllerTab(final JRibbon ribbon,
      final GeoToolMapRenderer _geoMapRenderer,
      final LiteStepControl stepControl, final TimeManager timeManager,
      final PlotOperations operations, final Layers layers,
      final UndoBuffer undoBuffer, final Runnable normalPainter,
      final Runnable snailPainter)
  {
    final JRibbonBand displayMode = createDisplayMode(normalPainter,
        snailPainter);

    final JRibbonBand filterToTime = createFilterToTime(stepControl, operations,
        timeManager);

    final JRibbonBand control = createControl(stepControl, timeManager, layers,
        undoBuffer, operations);

    final RibbonTask timeTask = new RibbonTask("Time", displayMode, control,
        filterToTime);
    ribbon.addTask(timeTask);
  }

  public static void assignThisTimeFormat(final String format,
      final boolean updateTimeLabel, final boolean updateFilters)
  {
    if (_menuItem != null && format != null)
    {
      for (int i = 0; i < _menuItem.length; i++)
      {
        _menuItem[i].setSelected(format.equals(_menuItem[i].getText()));
      }
      if (formatBinder != null)
      {
        formatBinder.updateTimeDateFormat(format, updateTimeLabel,
            updateFilters);
      }
    }
  }

  private static JRibbonBand createControl(final LiteStepControl stepControl,
      final TimeManager timeManager, final Layers layers,
      final UndoBuffer undoBuffer, final PlotOperations operations)
  {
    final JRibbonBand control = new JRibbonBand("Control", null);

    final JPanel controlPanel = new JPanel();
    controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
    controlPanel.setPreferredSize(new Dimension(500, 80));

    topButtonsPanel = new JPanel();
    topButtonsPanel.setLayout(new BoxLayout(topButtonsPanel, BoxLayout.X_AXIS));

    final JCommandButton behindCommandButton = MenuUtils.addCommandButton(
        "Behind", "icons/24/media_beginning.png", new AbstractAction()
        {

          /**
           *
           */
          private static final long serialVersionUID = 1L;

          @Override
          public void actionPerformed(final ActionEvent e)
          {
            timeManager.setTime(control, HiResDate.min(operations.getPeriod()
                .getStartDTG(), timeManager.getPeriod().getStartDTG()), true);
          }
        }, CommandButtonDisplayState.SMALL, "Move to start time");

    final JCommandButton rewindCommandButton = MenuUtils.addCommandButton(
        "Rewind", "icons/24/media_rewind.png", new AbstractAction()
        {

          /**
           *
           */
          private static final long serialVersionUID = 1L;

          @Override
          public void actionPerformed(final ActionEvent e)
          {
            stepControl.doStep(false, true);
          }
        }, CommandButtonDisplayState.SMALL, "Large step backwards");

    final JCommandButton backCommandButton = MenuUtils.addCommandButton("Back",
        "icons/24/media_back.png", new AbstractAction()
        {

          /**
           *
           */
          private static final long serialVersionUID = 1L;

          @Override
          public void actionPerformed(final ActionEvent e)
          {
            stepControl.doStep(false, false);
          }
        }, CommandButtonDisplayState.SMALL, "Small step backwards");

    final JCommandButton playCommandButton = MenuUtils.addCommandButton("Play",
        PLAY_IMAGE, new AbstractAction()
        {

          /**
           *
           */
          private static final long serialVersionUID = 1L;

          @Override
          public void actionPerformed(final ActionEvent e)
          {
            // ignore, we define the action once we've finished creating the button
          }
        }, CommandButtonDisplayState.SMALL, START_TEXT);

    playCommandButton.addActionListener(new ActionListener()
    {

      @Override
      public void actionPerformed(final ActionEvent e)
      {
        // what state are we in?
        final boolean isPlaying = stepControl.isPlaying();

        stepControl.startStepping(!isPlaying);

        // now update the play button UI
        updatePlayBtnUI(playCommandButton, isPlaying);
      }

    });

    final JCommandButton recordCommandButton = MenuUtils.addCommandButton(
        "Record", "icons/24/media_record.png", new AbstractAction()
        {

          /**
           *
           */
          private static final long serialVersionUID = 1L;

          @Override
          public void actionPerformed(final ActionEvent e)
          {
            JOptionPane.showMessageDialog(null,
                "Record to PPT not yet implemented.");

          }
        }, CommandButtonDisplayState.SMALL, "Start recording");

    final JCommandButton forwardCommandButton = MenuUtils.addCommandButton(
        "Forward", "icons/24/media_forward.png", new AbstractAction()
        {

          /**
           *
           */
          private static final long serialVersionUID = 1L;

          @Override
          public void actionPerformed(final ActionEvent e)
          {
            stepControl.doStep(true, false);
          }
        }, CommandButtonDisplayState.SMALL, "Small step forwards");

    final JCommandButton fastForwardCommandButton = MenuUtils.addCommandButton(
        "Fast Forward", "icons/24/media_fast_forward.png", new AbstractAction()
        {

          /**
           *
           */
          private static final long serialVersionUID = 1L;

          @Override
          public void actionPerformed(final ActionEvent e)
          {
            stepControl.doStep(true, true);
          }
        }, CommandButtonDisplayState.SMALL, "Large step forwards");

    final JCommandButton endCommandButton = MenuUtils.addCommandButton("End",
        "icons/24/media_end.png", new AbstractAction()
        {
          /**
           *
           */
          private static final long serialVersionUID = 1L;

          @Override
          public void actionPerformed(final ActionEvent e)
          {
            timeManager.setTime(control, HiResDate.max(operations.getPeriod()
                .getEndDTG(), timeManager.getPeriod().getEndDTG()), true);
          }
        }, CommandButtonDisplayState.SMALL, "Move to end time");

    final JCommandButton propertiesCommandButton = MenuUtils.addCommandButton(
        "Properties", "icons/16/properties.png", new AbstractAction()
        {
          /**
           *
           */
          private static final long serialVersionUID = 1973993003498667463L;

          @Override
          public void actionPerformed(final ActionEvent arg0)
          {
            ToolbarOwner owner = null;
            final ToolParent parent = stepControl.getParent();
            if (parent instanceof ToolbarOwner)
            {
              owner = (ToolbarOwner) parent;
            }
            final Layer parentLayer;
            if(parent instanceof Layer) {
              parentLayer = (Layer)parent;
            }
            else {
              parentLayer = null;
            }
            final PropertiesDialog dialog = new PropertiesDialog(stepControl
                .getInfo(), layers, undoBuffer, parent, owner,parentLayer);
            dialog.setSize(400, 500);
            dialog.setLocationRelativeTo(null);
            dialog.setVisible(true);
          }
        }, CommandButtonDisplayState.SMALL, "Edit time-step properties");

    // we need to give the menu to the command popup
    final JPopupMenu menu = new JPopupMenu();

    final JCommandButton formatCommandButton = MenuUtils.addCommandButton(
        "Format", "icons/24/gears_view.png", new ShowFormatAction(menu),
        CommandButtonDisplayState.SMALL, "Format time control");

    final JLabel timeLabel = new JLabel(LiteStepControl.timeFormat)
    {
      /**
       *
       */
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(final Graphics g)
      {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());
        super.paintComponent(g);
      }
    };
    timeLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 16));

    timeLabel.setForeground(new Color(0, 255, 0));

    _menuItem = new JCheckBoxMenuItem[timeFormats.length];
    for (int i = 0; i < timeFormats.length; i++)
    {
      _menuItem[i] = new JCheckBoxMenuItem(timeFormats[i]);
    }

    resetDateFormat();

    final ActionListener selfAssignFormat = new ActionListener()
    {
      @Override
      public void actionPerformed(final ActionEvent e)
      {
        final String format = e.getActionCommand();
        assignThisTimeFormat(format, true, false);
      }
    };

    for (int i = 0; i < timeFormats.length; i++)
    {
      _menuItem[i].addActionListener(selfAssignFormat);
      menu.add(_menuItem[i]);
    }

    topButtonsPanel.add(behindCommandButton);
    topButtonsPanel.add(rewindCommandButton);
    topButtonsPanel.add(backCommandButton);
    topButtonsPanel.add(playCommandButton);
    topButtonsPanel.add(recordCommandButton);
    topButtonsPanel.add(forwardCommandButton);
    topButtonsPanel.add(fastForwardCommandButton);
    topButtonsPanel.add(endCommandButton);
    topButtonsPanel.add(new JLabel(" | "));
    topButtonsPanel.add(propertiesCommandButton);
    topButtonsPanel.add(timeLabel);
    topButtonsPanel.add(formatCommandButton);

    controlPanel.add(topButtonsPanel);
    final JSlider timeSlider = new JSlider();
    timeSlider.setPreferredSize(new Dimension(420, 30));
    timeSlider.setEnabled(false);

    label = new TimeLabel()
    {

      @Override
      public void setFontSize(final int newSize)
      {
        final Font originalFont = timeLabel.getFont();
        final Font newFont = new Font(originalFont.getName(), originalFont
            .getStyle(), newSize);
        timeLabel.setFont(newFont);
      }

      @Override
      public void setRange(final long start, final long end)
      {
        // ok, we can use time slider
        timeSlider.setEnabled(true);

        // and we can use the buttons
        DebriefLiteApp.setState(DebriefLiteApp.ACTIVE_STATE);

        converter.init(start, end);
        timeSlider.setMinimum(converter.getStart());
        timeSlider.setMaximum(converter.getEnd());
      }

      @Override
      public void setValue(final long time)
      {
        // find the value
        final int value = converter.getCurrentAt(time);
        timeSlider.setValue(value);
      }

      @Override
      public void setValue(final String text)
      {

        final int completeSize = 17;
        final int diff = completeSize - text.length();

        String newText = text;
        for (int i = 0; i < diff / 2; i++)
        {
          newText = " " + newText + " ";
        }
        if (newText.length() < completeSize)
        {
          newText = newText + " ";
        }
        timeLabel.setText(newText);
      }
    };
    stepControl.setTimeLabel(label);

    // we also need to listen to the slider
    timeSlider.addChangeListener(new ChangeListener()
    {
      @Override
      public void stateChanged(final ChangeEvent e)
      {
        final int pos = timeSlider.getValue();
        final long time = converter.getTimeAt(pos);
        if (timeManager.getTime() == null || timeManager.getTime().getDate()
            .getTime() != time)
        {
          timeManager.setTime(timeSlider, new HiResDate(time), true);
        }
      }
    });

    // ok, start off with the buttons disabled
    setButtonsEnabled(topButtonsPanel, false);


    
    final DataListener updateTimeController = new DataListener()
    {
      
      @Override
      public void dataReformatted(Layers theData, Layer changedLayer)
      {
        updateTimeController();
      }
      
      @Override
      public void dataModified(Layers theData, Layer changedLayer)
      {
        updateTimeController();
      }
      
      @Override
      public void dataExtended(Layers theData)
      {
        updateTimeController();
      }
      
      private void updateTimeController()
      {
        stepControl.startStepping(false);
        boolean hasTracks = false;
        
        final Enumeration<Editable> lIter = stepControl.getLayers().elements();
        while (lIter.hasMoreElements())
        {
          final Editable next = lIter.nextElement();
          if (next instanceof TrackWrapper)
          {
            hasTracks = true;
            break;
          }
        }
        
        if ( !hasTracks )
        {
          doSoftReset(timeSlider, timeManager);
        }else
        {
          DebriefLiteApp.setDirty(true);
          DebriefLiteApp.setState(DebriefLiteApp.ACTIVE_STATE);
          timeSlider.setEnabled(true);
        }
      }
    };
    
    // we also need to listen out for the stepper control mode changing
    stepControl.addStepperListener(new LiteStepperListener(playCommandButton)
    {

      @Override
      public void reset()
      {
        doSoftReset(timeSlider, timeManager);
      }
    });
    
    stepControl.getLayers().addDataExtendedListener(updateTimeController);
    stepControl.getLayers().addDataModifiedListener(updateTimeController);

    control.addRibbonComponent(new JRibbonComponent(topButtonsPanel));
    control.addRibbonComponent(new JRibbonComponent(timeSlider));

    control.setResizePolicies(MenuUtils.getStandardRestrictivePolicies(
        control));
    return control;
  }
  
  public static void doSoftReset(final JSlider timeSlider, final TimeManager timeManager)
  {
    // move the slider to the start
    timeSlider.setValue(0);
    label.setValue(LiteStepControl.timeFormat);

    // ok, do some disabling
    DebriefLiteApp.setState(DebriefLiteApp.INACTIVE_STATE);
    timeSlider.setEnabled(false);
    timeManager.setPeriod(null, null);
    formatBinder.reset();
  }

  private static JRibbonBand createDisplayMode(final Runnable normalPainter,
      final Runnable snailPainter)
  {
    final JRibbonBand displayMode = new JRibbonBand("Display Mode", null);
    final FlamingoCommandToggleGroup displayModeGroup =
        new FlamingoCommandToggleGroup();
    MenuUtils.addCommandToggleButton("Normal", "icons/48/normal.png",
        new AbstractAction()
        {
          private static final long serialVersionUID = 1L;

          @Override
          public void actionPerformed(final ActionEvent e)
          {
            normalPainter.run();
          }
        }, displayMode, RibbonElementPriority.TOP, true, displayModeGroup,
        true);
    MenuUtils.addCommandToggleButton("Snail", "icons/48/snail.png",
        new AbstractAction()
        {
          private static final long serialVersionUID = 1L;

          @Override
          public void actionPerformed(final ActionEvent e)
          {
            snailPainter.run();
          }
        }, displayMode, RibbonElementPriority.TOP, true, displayModeGroup,
        false);

    displayMode.setResizePolicies(MenuUtils.getStandardRestrictivePolicies(
        displayMode));

    return displayMode;
  }

  private static JRibbonBand createFilterToTime(
      final LiteStepControl stepControl, final PlotOperations operations,
      final TimeManager timeManager)
  {
    final JRibbonBand timePeriod = new JRibbonBand("Filter to time", null);

    final Calendar start = new GregorianCalendar(1995, 11, 12);
    final Calendar end = new GregorianCalendar(1995, 11, 12);
    // Now we create the components for the sliders
    final JLabel minimumValue = new JLabel();
    final JLabel maximumValue = new JLabel();
    final RangeSlider slider = new RangeSlider(start, end);

    formatBinder.stepControl = stepControl;
    formatBinder.maximumValue = maximumValue;
    formatBinder.minimumValue = minimumValue;
    formatBinder.slider = slider;
    formatBinder.timeManager = timeManager;

    formatBinder.updateFilterDateFormat();
    slider.addChangeListener(new SliderListener(operations, timeManager));
    slider.setEnabled(false);
    slider.setPreferredSize(new Dimension(250, 200));

    final JPanel sliderPanel = new JPanel();
    sliderPanel.setLayout(new BoxLayout(sliderPanel, BoxLayout.Y_AXIS));
    sliderPanel.setPreferredSize(new Dimension(250, 200));

    // Label's panel
    final JPanel valuePanel = new JPanel();
    valuePanel.setLayout(new BoxLayout(valuePanel, BoxLayout.X_AXIS));

    valuePanel.add(minimumValue);
    valuePanel.add(Box.createGlue());
    valuePanel.add(maximumValue);
    valuePanel.setPreferredSize(new Dimension(250, 200));

    timePeriod.addRibbonComponent(new JRibbonComponent(slider));
    timePeriod.addRibbonComponent(new JRibbonComponent(valuePanel));

    // tie in to the stepper
    final SliderControls iSlider = new LiteSliderControls(slider);
    stepControl.setSliderControls(iSlider);

    // listen out for time being reset
    // we also need to listen out for the stepper control mode changing
    stepControl.addStepperListener(new LiteStepperListener(null)
    {

      @Override
      public void reset()
      {
        minimumValue.setText(" ");
        maximumValue.setText(" ");
      }
    });

    return timePeriod;
  }

  public static void resetDateFormat()
  {
    final String defaultFormat = LiteStepControl.timeFormat;
    if (defaultFormat != null)
    {
      DebriefRibbonTimeController.assignThisTimeFormat(defaultFormat, false,
          false);

      formatBinder.stepControl.setDateFormat(defaultFormat);
      formatBinder.updateFilterDateFormat();
    }

    if (label != null)
    {
      label.setValue(defaultFormat);
    }

  }

  /**
   * convenience class to bulk enable/disable controls in a panel
   *
   * @param panel
   * @param enabled
   */
  public static void setButtonsEnabled(final JPanel panel,
      final boolean enabled)
  {
    final Component[] items = panel.getComponents();
    for (final Component item : items)
    {
      final boolean state = item.isEnabled();
      if (state != enabled)
      {
        item.setEnabled(enabled);
      }
    }
  }

  public static void updatePlayBtnUI(final JCommandButton playCommandButton,
      final boolean isPlaying)
  {
    final String image;
    if (isPlaying)
      image = PLAY_IMAGE;
    else
      image = STOP_IMAGE;

    final String tooltip = isPlaying ? STOP_TEXT : START_TEXT;

    final RichTooltipBuilder builder = new RichTooltipBuilder();
    final RichTooltip richTooltip = builder.setTitle("Timer")
        .addDescriptionSection(tooltip).build();
    playCommandButton.setActionRichTooltip(richTooltip);

    // switch the icon
    final Image playStopinImage = MenuUtils.createImage(image);
    final ImageWrapperResizableIcon imageIcon = ImageWrapperResizableIcon
        .getIcon(playStopinImage, MenuUtils.ICON_SIZE_16);

    playCommandButton.setExtraText(tooltip);

    playCommandButton.setIcon(imageIcon);
  }
}
