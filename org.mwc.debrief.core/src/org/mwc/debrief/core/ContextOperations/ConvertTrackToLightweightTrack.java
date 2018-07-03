/*
 *    Debrief - the Open Source Maritime Analysis Application
 *    http://debrief.info
 *
 *    (C) 2000-2014, PlanetMayo Ltd
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the Eclipse Public License v1.0
 *    (http://www.eclipse.org/legal/epl-v10.html)
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 */
package org.mwc.debrief.core.ContextOperations;

import java.awt.Color;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Vector;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.mwc.cmap.core.CorePlugin;
import org.mwc.cmap.core.operations.CMAPOperation;
import org.mwc.cmap.core.property_support.RightClickSupport.RightClickContextItemGenerator;

import Debrief.Wrappers.FixWrapper;
import Debrief.Wrappers.LabelWrapper;
import Debrief.Wrappers.ShapeWrapper;
import Debrief.Wrappers.TrackWrapper;
import Debrief.Wrappers.Track.LightweightTrackWrapper;
import MWC.GUI.BaseLayer;
import MWC.GUI.Editable;
import MWC.GUI.Layer;
import MWC.GUI.Layers;
import MWC.GUI.Plottable;
import MWC.GUI.Properties.DebriefColors;
import MWC.GUI.Shapes.LineShape;
import MWC.GUI.Shapes.PlainShape;
import MWC.GenericData.HiResDate;
import MWC.GenericData.WorldLocation;
import MWC.TacticalData.Fix;

/**
 * @author ian.mayo
 * 
 */
public class ConvertTrackToLightweightTrack implements
    RightClickContextItemGenerator
{

  /**
   * @param parent
   * @param theLayers
   * @param parentLayers
   * @param subjects
   */
  public void generate(final IMenuManager parent, final Layers theLayers,
      final Layer[] parentLayers, final Editable[] subjects)
  {
    int layersValidForConvertToLightweight = 0;

    // right, work through the subjects
    for (int i = 0; i < subjects.length; i++)
    {
      final Editable thisE = subjects[i];
      if (thisE instanceof TrackWrapper)
      {
        // ok, we've started...
        layersValidForConvertToLightweight++;
      }
      else
      {
        return;
      }
    }

    // ok, is it worth going for?
    if (layersValidForConvertToLightweight > 0)
    {
      final String title;
      if (layersValidForConvertToLightweight > 1)
        title = "tracks";
      else
        title = "track";

      // right,stick in a separator
      parent.add(new Separator());

      MenuManager listing = new MenuManager("Convert to lightweight " + title
          + " in...");

      // ok, determine list of suitable targets
      Enumeration<Editable> ele = theLayers.elements();
      while (ele.hasMoreElements())
      {
        Editable ed = ele.nextElement();
        if (ed instanceof BaseLayer)
        {
          final BaseLayer target = (BaseLayer) ed;

          // yes, create the action
          final Action convertToTrack = new Action(target.getName())
          {
            public void run()
            {
              // ok, go for it.
              // sort it out as an operation
              final IUndoableOperation convertToTrack1 = new ConvertIt(title,
                  theLayers, subjects, target);

              // ok, stick it on the buffer
              runIt(convertToTrack1);
            }
          };

          // ok - flash up the menu item
          listing.add(convertToTrack);
        }
      }
      
      // and a spare one, which creates a new layer
      final Action convertToTrackInNewLayer = new Action("New layer...")
      {
        public void run()
        {
          // get the name
          NameDialog dialog = new NameDialog(new Shell());
          dialog.open();
          String name = dialog.getName();
          if(name != null)
          {
            String tName = name.trim();
            
            // create the layer
            BaseLayer layer = new BaseLayer();
            layer.setName(tName);
            
            // store it 
            theLayers.addThisLayer(layer);
            
            // ok, go for it.
            // sort it out as an operation
            final IUndoableOperation convertToTrack1 = new ConvertIt(title,
                theLayers, subjects, layer);

            // ok, stick it on the buffer
            runIt(convertToTrack1);
          }
          
        }        
      };

      // ok - flash up the menu item
      listing.add(convertToTrackInNewLayer);
      
      // done
      parent.add(listing);

    }

  }
  
  private static class NameDialog extends Dialog {
    private Text nameField;
    private String nameString;

    public NameDialog(Shell parentShell) {
        super(parentShell);
    }

    @Override
    protected void configureShell(Shell newShell)
    {
        super.configureShell(newShell);
        newShell.setText("Please provide layer name");
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite comp = (Composite) super.createDialogArea(parent);

        GridLayout layout = (GridLayout) comp.getLayout();
        layout.numColumns = 2;

        Label nameLabel = new Label(comp, SWT.RIGHT);
        nameLabel.setText("Layer name:");
        nameField = new Text(comp, SWT.SINGLE | SWT.BORDER);

        GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false);
        nameField.setLayoutData(data);

        return comp;
    }

    @Override
    protected void okPressed()
    {
        nameString = nameField.getText();
        super.okPressed();
    }

    @Override
    protected void cancelPressed()
    {
        nameField.setText("");
        super.cancelPressed();
    }

    public String getName()
    {
        return nameString;
    }
}

  /**
   * put the operation firer onto the undo history. We've refactored this into a separate method so
   * testing classes don't have to simulate the CorePlugin
   * 
   * @param operation
   */
  protected void runIt(final IUndoableOperation operation)
  {
    CorePlugin.run(operation);
  }

  private static class ConvertIt extends CMAPOperation
  {

    private final Layers _layers;
    private final Editable[] _subjects;

    private Vector<LightweightTrackWrapper> _newLightweights;
    private Vector<TrackWrapper> _oldTracks;
    private BaseLayer _targetLayer;

    public ConvertIt(final String title, final Layers layers,
        final Editable[] subjects, BaseLayer target)
    {
      super(title);
      _layers = layers;
      _subjects = subjects;
      _targetLayer = target;
    }

    public IStatus execute(final IProgressMonitor monitor,
        final IAdaptable info) throws ExecutionException
    {
      _newLightweights = new Vector<LightweightTrackWrapper>();
      _oldTracks = new Vector<TrackWrapper>();

      // right, get going through the track
      for (int i = 0; i < _subjects.length; i++)
      {
        final Editable thisE = _subjects[i];
        if (thisE instanceof TrackWrapper)
        {
          final TrackWrapper oldTrack = (TrackWrapper) thisE;

          // switch off the layer
          oldTrack.setVisible(false);

          final LightweightTrackWrapper newTrack = new LightweightTrackWrapper(
              oldTrack.getName(), oldTrack.getVisible(), oldTrack
                  .getNameVisible(), oldTrack.getColor(), oldTrack
                      .getLineStyle());

          _newLightweights.add(newTrack);
          _oldTracks.add(oldTrack);
          
          // put it into the layer
          _targetLayer.add(newTrack);

          newTrack.setName(oldTrack.getName());
          final Color hisColor = oldTrack.getCustomColor();
          if (hisColor != null)
          {
            newTrack.setColor(hisColor);
          }
          else
          {
            newTrack.setColor(DebriefColors.GOLD);
          }

          final Enumeration<Editable> numer = oldTrack.getPositionIterator();
          while (numer.hasMoreElements())
          {
            final FixWrapper fix = (FixWrapper) numer.nextElement();
            newTrack.add(fix);
          }
          
          // actually, ditch the old track
          _layers.removeThisLayer(oldTrack);
        }
      }

      // sorted, do the update
      _layers.fireExtended();

      return Status.OK_STATUS;
    }

    public IStatus undo(final IProgressMonitor monitor, final IAdaptable info)
        throws ExecutionException
    {
      // forget about the new tracks
      for (final Iterator<LightweightTrackWrapper> iter = _newLightweights
          .iterator(); iter.hasNext();)
      {
        final LightweightTrackWrapper trk = (LightweightTrackWrapper) iter.next();
        _targetLayer.removeElement(trk);
      }

      for (TrackWrapper t : _oldTracks)
      {
        t.setVisible(true);
        
        _layers.addThisLayer(t);
      }

      // and clear the new tracks item
      _newLightweights.removeAllElements();
      _newLightweights = null;

      _oldTracks.removeAllElements();
      _oldTracks = null;
      
      return Status.OK_STATUS;
    }

  }

  /**
   * find out if this item is suitable for use as a track item
   * 
   * @param thisP
   * @return
   */
  static boolean isSuitableAsTrackPoint(final Plottable thisP)
  {
    boolean res = false;

    // ok - is it a label? Converting that to a track point is quite easy
    if (thisP instanceof LabelWrapper)
    {
      res = true;
    }

    // next, see if it's a line, because the pretend track could have been
    // drawn up as a series of lines
    if (thisP instanceof ShapeWrapper)
    {
      final ShapeWrapper sw = (ShapeWrapper) thisP;
      final PlainShape shp = sw.getShape();
      if (shp instanceof LineShape)
        res = true;
    }
    return res;
  }

  public static TrackWrapper generateTrackFor(final BaseLayer layer)
  {
    TrackWrapper res = new TrackWrapper();
    res.setName("T_" + layer.getName());

    Color trackColor = null;

    // ok, step through the points
    final Enumeration<Editable> numer = layer.elements();

    // remember the last line viewed, since we want to add both of it's points
    ShapeWrapper lastLine = null;

    while (numer.hasMoreElements())
    {
      final Plottable pl = (Plottable) numer.nextElement();
      if (pl instanceof LabelWrapper)
      {
        final LabelWrapper label = (LabelWrapper) pl;

        // just check we know the track color
        if (trackColor == null)
          trackColor = label.getColor();

        HiResDate dtg = label.getStartDTG();
        if (dtg == null)
          dtg = new HiResDate(new Date());

        final WorldLocation loc = label.getBounds().getCentre();
        final Fix newFix = new Fix(dtg, loc, 0, 0);
        final FixWrapper fw = new FixWrapper(newFix);

        if (label.getColor() != trackColor)
          fw.setColor(label.getColor());

        res.add(fw);
        fw.setTrackWrapper(res);

        // forget the last-line, clearly we've moved on to other things
        lastLine = null;

      }
      else if (pl instanceof ShapeWrapper)
      {
        final ShapeWrapper sw = (ShapeWrapper) pl;
        final PlainShape shape = sw.getShape();
        if (shape instanceof LineShape)
        {
          final LineShape line = (LineShape) shape;
          // just check we know the track color
          if (trackColor == null)
            trackColor = line.getColor();

          final HiResDate dtg = sw.getStartDTG();
          final WorldLocation loc = line.getLine_Start();
          final Fix newFix = new Fix(dtg, loc, 0, 0);
          final FixWrapper fw = new FixWrapper(newFix);

          if (line.getColor() != trackColor)
            fw.setColor(line.getColor());
          fw.setTrackWrapper(res);
          res.add(fw);

          // and remember this line
          lastLine = sw;

        }
      }
    }

    // did we have a trailing line item?
    if (lastLine != null)
    {
      final HiResDate dtg = lastLine.getEndDTG();
      final LineShape line = (LineShape) lastLine.getShape();
      final WorldLocation loc = line.getLineEnd();
      final Fix newFix = new Fix(dtg, loc, 0, 0);
      final FixWrapper fw = new FixWrapper(newFix);
      fw.setTrackWrapper(res);
      res.add(fw);
    }

    // update the track color
    res.setColor(trackColor);

    // did we find any?
    if (res.numFixes() == 0)
      res = null;

    return res;
  }

  // ////////////////////////////////////////////////////////////////////////////////////////////////
  // testing for this class
  // ////////////////////////////////////////////////////////////////////////////////////////////////
  static public final class testMe extends junit.framework.TestCase
  {
    static public final String TEST_ALL_TEST_TYPE = "UNIT";

    public testMe(final String val)
    {
      super(val);
    }

    public final void testIWork()
    {
      final Layers theLayers = new Layers();
      final BaseLayer holder = new BaseLayer();
      holder.setName("Trk");
      theLayers.addThisLayer(holder);

      WorldLocation lastLoc = null;
      for (int i = 0; i < 4; i++)
      {
        final WorldLocation thisLoc = new WorldLocation(0, i, 0, 'N', 0, 0, 0,
            'W', 0);
        if (lastLoc != null)
        {
          // ok, add the line
          final LineShape ls = new LineShape(lastLoc, thisLoc);

          final long theDate1 = 20000000 + i * 60000;
          final long theDate2 = 20000000 + i * 61000;

          final ShapeWrapper sw = new ShapeWrapper("shape:" + i, ls, Color.red,
              new HiResDate(theDate1));
          sw.setTime_Start(new HiResDate(theDate1));
          sw.setTimeEnd(new HiResDate(theDate2));
          holder.add(sw);
        }

        // and remember the last location
        lastLoc = thisLoc;
      }

      // ok, now do the interpolation
      final ConvertIt ct = new ConvertIt("convert it", theLayers, new Editable[]
      {holder}, null);

      try
      {
        ct.execute(null, null);
      }
      catch (final ExecutionException e)
      {
        fail("Exception thrown");
      }

      // check the track got generated
      final TrackWrapper tw = (TrackWrapper) theLayers.findLayer("T_Trk");

      // did we find it?
      assertNotNull("track generated", tw);

      // check we've got the right number of fixes
      assertEquals("right num of fixes generated", tw.numFixes(), 4);

    }
  }
}
