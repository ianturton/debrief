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
package org.mwc.debrief.track_shift.views;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Paint;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Vector;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IStatus;
import org.jfree.data.general.Series;
import org.jfree.data.general.SeriesException;
import org.jfree.data.time.FixedMillisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.time.TimeSeriesDataItem;
import org.jfree.util.ShapeUtilities;
import org.mwc.cmap.core.CorePlugin;
import org.mwc.debrief.core.ContextOperations.GenerateTMASegmentFromCuts.TMAfromCuts;
import org.mwc.debrief.track_shift.controls.ZoneChart;
import org.mwc.debrief.track_shift.controls.ZoneChart.ColorProvider;
import org.mwc.debrief.track_shift.controls.ZoneChart.Zone;
import org.mwc.debrief.track_shift.controls.ZoneChart.ZoneSlicer;
import org.mwc.debrief.track_shift.zig_detector.ArtificalLegDetector;
import org.mwc.debrief.track_shift.zig_detector.IOwnshipLegDetector;
import org.mwc.debrief.track_shift.zig_detector.Precision;
import org.mwc.debrief.track_shift.zig_detector.ownship.LegOfData;
import org.mwc.debrief.track_shift.zig_detector.ownship.PeakTrackingOwnshipLegDetector;

import Debrief.GUI.Frames.Application;
import Debrief.ReaderWriter.Replay.ImportReplay;
import Debrief.Wrappers.FixWrapper;
import Debrief.Wrappers.ISecondaryTrack;
import Debrief.Wrappers.SensorContactWrapper;
import Debrief.Wrappers.SensorWrapper;
import Debrief.Wrappers.TrackWrapper;
import Debrief.Wrappers.Track.Doublet;
import Debrief.Wrappers.Track.DynamicInfillSegment;
import Debrief.Wrappers.Track.RelativeTMASegment;
import Debrief.Wrappers.Track.TrackSegment;
import Debrief.Wrappers.Track.TrackWrapper_Support.SegmentList;
import MWC.Algorithms.FrequencyCalcs;
import MWC.GUI.BaseLayer;
import MWC.GUI.Editable;
import MWC.GUI.ErrorLogger;
import MWC.GUI.Layers;
import MWC.GUI.LoggingService;
import MWC.GUI.PlainWrapper;
import MWC.GUI.JFreeChart.ColourStandardXYItemRenderer;
import MWC.GUI.JFreeChart.ColouredDataItem;
import MWC.GenericData.HiResDate;
import MWC.GenericData.TimePeriod;
import MWC.GenericData.Watchable;
import MWC.GenericData.WatchableList;
import MWC.GenericData.WorldDistance;
import MWC.GenericData.WorldDistance.ArrayLength;
import MWC.GenericData.WorldLocation;
import MWC.GenericData.WorldSpeed;
import MWC.GenericData.WorldVector;
import MWC.TacticalData.Fix;
import MWC.TacticalData.TrackDataProvider;
import junit.framework.TestCase;

public final class StackedDotHelper
{

  /**
   * convenience class, to avoid having to pass plot into data helper
   *
   */
  public static interface SetBackgroundShade
  {
    void setShade(final Paint errorColor);
  }

  /**
   * special listener, that knows how to detatch itself
   *
   * @author ian
   *
   */
  private abstract static class PrivatePropertyChangeListener implements
      PropertyChangeListener
  {
    private final TrackWrapper _track;
    private final String _property;

    public PrivatePropertyChangeListener(final TrackWrapper track,
        final String property)
    {
      _track = track;
      _property = property;
    }

    public void detach()
    {
      _track.removePropertyChangeListener(_property, this);
    }

  }

  private static class TargetDoublet
  {

    public TrackSegment targetParent;
    public FixWrapper targetFix;

  }

  public static class TestUpdates extends TestCase
  {
    public TrackDataHelper getTrackData() throws ExecutionException
    {
      final Layers layers = getData();
      final TrackWrapper ownship = (TrackWrapper) layers.findLayer("SENSOR");
      assertNotNull("found ownship", ownship);

      final BaseLayer sensors = ownship.getSensors();
      assertEquals("has all sensors", 2, sensors.size());

      // get the tail
      final SensorWrapper tailSensor = (SensorWrapper) sensors.find(
          "tail sensor");
      assertNotNull("found tail", tailSensor);
      final SensorWrapper hullSensor = (SensorWrapper) sensors.find(
          "hull sensor");
      assertNotNull("found hull", hullSensor);

      // give it it's offset
      tailSensor.setSensorOffset(new ArrayLength(1000));

      SensorContactWrapper[] tailItems = getAllCutsFrom(tailSensor);
      SensorContactWrapper[] hullItems = getAllCutsFrom(hullSensor);

      // note: there are only 9 cuts, since we've hidden one
      assertEquals("got all cuts", 9, tailItems.length);
      assertEquals("got all cuts", 10, hullItems.length);

      final String newName = "TMA_LEG";

      // ok, we also have to generate some target track
      TMAfromCuts genny = new TMAfromCuts(tailItems, layers, new WorldVector(
          Math.PI / 2, 0.02, 0), 45, new WorldSpeed(12, WorldSpeed.Kts),
          Color.RED)
      {
        @Override
        public String getTrackNameFor(TrackWrapper newTrack)
        {
          return newName;
        }
      };

      // create the new TMA
      genny.execute(null, null);

      // get the TMA
      final TrackWrapper tma = (TrackWrapper) layers.findLayer(newName);
      assertNotNull("found it", tma);

      // have a butchers
      assertEquals("has segments", 1, tma.getSegments().size());
      Collection<Editable> fixes = tma.getUnfilteredItems(new HiResDate(0),
          new HiResDate(new Date().getTime()));

      // note: only 9 fixes in leg, since one sensor cut was hidden
      assertEquals("has fixes", 9, fixes.size());

      // and now the track data object
      TrackDataHelper prov = new TrackDataHelper();
      prov.addPrimary(ownship);
      prov.addSecondary(tma);

      return prov;
    }

    private static class TrackDataHelper implements TrackDataProvider
    {

      private List<TrackWrapper> _primaries = new ArrayList<TrackWrapper>();
      private List<TrackWrapper> _secondaries = new ArrayList<TrackWrapper>();

      @Override
      public void addTrackDataListener(TrackDataListener listener)
      {
      }

      public void addSecondary(TrackWrapper tma)
      {
        _secondaries.add(tma);
      }

      public void addPrimary(TrackWrapper ownship)
      {
        _primaries.add(ownship);
      }

      @Override
      public void removeTrackDataListener(TrackDataListener listener)
      {
      }

      @Override
      public void addTrackShiftListener(TrackShiftListener listener)
      {
      }

      @Override
      public void removeTrackShiftListener(TrackShiftListener listener)
      {
      }

      @Override
      public void fireTrackShift(WatchableList watchableList)
      {
      }

      @Override
      public void fireTracksChanged()
      {
      }

      @Override
      public WatchableList getPrimaryTrack()
      {
        return _primaries.get(0);
      }

      @Override
      public WatchableList[] getSecondaryTracks()
      {
        WatchableList[] res = new WatchableList[_secondaries.size()];
        int ctr = 0;
        Iterator<TrackWrapper> sIter = _secondaries.iterator();
        while (sIter.hasNext())
        {
          res[ctr++] = sIter.next();
        }
        return res;
      }

    }

    private SensorContactWrapper[] getAllCutsFrom(SensorWrapper secondSensor)
    {
      SensorContactWrapper[] res = new SensorContactWrapper[secondSensor
          .size()];
      Enumeration<Editable> sIter = secondSensor.elements();
      int ctr = 0;
      while (sIter.hasMoreElements())
      {
        res[ctr++] = (SensorContactWrapper) sIter.nextElement();
      }

      return res;
    }

    public Layers getData()
    {
      Layers layers = new Layers();
      ImportReplay importer = new ImportReplay();
      importer.setLayers(layers);

      try
      {
        importer.readLine(
            "100112 120000 SUBJECT VC 60 06 00.00 N 000 15 00.00 E 320.00  9.00  0.00");
        importer.readLine(
            "100112 120000 SENSOR FA 60 10 48.00 N 000 12 00.00 E 200.00  12.00  0.00");
        importer.readLine(
            ";SENSOR2: 100112 120000 SENSOR @A NULL 162.64 237.36 150.910 NULL \"hull sensor\" SUBJECT held on hull sensor");
        importer.readLine(
            ";SENSOR2: 100112 120000 SENSOR @A NULL 166.15 233.85 150.920 NULL \"tail sensor\" SUBJECT held on tail sensor");
        importer.readLine(
            "100112 120020 SUBJECT VC 60 06 02.30 N 000 14 56.13 E 320.00  9.00  0.00 ");
        importer.readLine(
            "100112 120020 SENSOR FA 60 10 44.24 N 000 11 57.25 E 200.00  12.00  0.00 ");
        importer.readLine(
            ";SENSOR2: 100112 120020 SENSOR @A NULL 162.39 237.61 150.909 NULL \"hull sensor\" SUBJECT held on hull sensor");
        importer.readLine(
            ";SENSOR2: 100112 120020 SENSOR @A NULL 165.99 234.01 150.919 NULL \"tail sensor\" SUBJECT held on tail sensor");
        importer.readLine(
            "100112 120040 SUBJECT VC 60 06 04.60 N 000 14 52.26 E 320.00  9.00  0.00 ");
        importer.readLine(
            "100112 120040 SENSOR FA 60 10 40.48 N 000 11 54.50 E 200.00  12.00  0.00 ");
        importer.readLine(
            ";SENSOR2: 100112 120040 SENSOR @A NULL 162.13 237.87 150.908 NULL \"hull sensor\" SUBJECT held on hull sensor");
        importer.readLine(
            ";SENSOR2: 100112 120040 SENSOR @A NULL 165.82 234.18 150.919 NULL \"tail sensor\" SUBJECT held on tail sensor");
        importer.readLine(
            "100112 120100 SUBJECT VC 60 06 06.89 N 000 14 48.39 E 320.00  9.00  0.00 ");
        importer.readLine(
            "100112 120100 SENSOR FA 60 10 36.72 N 000 11 51.75 E 200.00  12.00  0.00 ");
        importer.readLine(
            ";SENSOR2: 100112 120100 SENSOR @A NULL 161.87 238.13 150.907 NULL \"hull sensor\" SUBJECT held on hull sensor");
        // miss this tail measurement
        // importer.readLine(
        // ";SENSOR2: 100112 120100 SENSOR @A NULL 165.64 234.36 150.918 NULL \"tail sensor\"
        // SUBJECT held on tail sensor");
        importer.readLine(
            "100112 120120 SUBJECT VC 60 06 09.19 N 000 14 44.53 E 320.00  9.00  0.00 ");
        importer.readLine(
            "100112 120120 SENSOR FA 60 10 32.96 N 000 11 49.00 E 200.00  12.00  0.00 ");
        importer.readLine(
            ";SENSOR2: 100112 120120 SENSOR @A NULL 161.59 238.41 150.906 NULL \"hull sensor\" SUBJECT held on hull sensor");
        importer.readLine(
            ";SENSOR2: 100112 120120 SENSOR @A NULL 165.46 234.54 150.918 NULL \"tail sensor\" SUBJECT held on tail sensor");
        importer.readLine(
            "100112 120140 SUBJECT VC 60 06 11.49 N 000 14 40.66 E 320.00  9.00  0.00 ");
        importer.readLine(
            "100112 120140 SENSOR FA 60 10 29.21 N 000 11 46.25 E 200.00  12.00  0.00 ");
        importer.readLine(
            ";SENSOR2: 100112 120140 SENSOR @A NULL 161.29 238.71 150.905 NULL \"hull sensor\" SUBJECT held on hull sensor");
        importer.readLine(
            ";SENSOR2: 100112 120140 SENSOR @A NULL 165.27 234.73 150.918 NULL \"tail sensor\" SUBJECT held on tail sensor");
        importer.readLine(
            "100112 120200 SUBJECT VC 60 06 13.79 N 000 14 36.79 E 320.00  9.00  0.00 ");
        importer.readLine(
            "100112 120200 SENSOR FA 60 10 25.45 N 000 11 43.49 E 200.00  12.00  0.00 ");
        importer.readLine(
            ";SENSOR2: 100112 120200 SENSOR @A NULL 160.99 239.01 150.904 NULL \"hull sensor\" SUBJECT held on hull sensor");
        importer.readLine(
            ";SENSOR2: 100112 120200 SENSOR @A NULL 165.08 234.92 150.917 NULL \"tail sensor\" SUBJECT held on tail sensor");
        importer.readLine(
            "100112 120220 SUBJECT VC 60 06 16.09 N 000 14 32.92 E 320.00  9.00  0.00 ");
        importer.readLine(
            "100112 120220 SENSOR FA 60 10 21.69 N 000 11 40.74 E 200.00  12.00  0.00 ");
        importer.readLine(
            ";SENSOR2: 100112 120220 SENSOR @A NULL 160.67 239.33 150.902 NULL \"hull sensor\" SUBJECT held on hull sensor");
        importer.readLine(
            ";SENSOR2: 100112 120220 SENSOR @A NULL 164.87 235.13 150.916 NULL \"tail sensor\" SUBJECT held on tail sensor");
        importer.readLine(
            "100112 120240 SUBJECT VC 60 06 18.39 N 000 14 29.05 E 320.00  9.00  0.00 ");
        importer.readLine(
            "100112 120240 SENSOR FA 60 10 17.93 N 000 11 37.99 E 200.00  12.00  0.00 ");
        importer.readLine(
            ";SENSOR2: 100112 120240 SENSOR @A NULL 160.33 239.67 150.901 NULL \"hull sensor\" SUBJECT held on hull sensor");
        importer.readLine(
            ";SENSOR2: 100112 120240 SENSOR @A NULL 164.66 235.34 150.916 NULL \"tail sensor\" SUBJECT held on tail sensor");
        importer.readLine(
            "100112 120300 SUBJECT VC 60 06 20.68 N 000 14 25.18 E 320.00  9.00  0.00 ");
        importer.readLine(
            "100112 120300 SENSOR FA 60 10 14.17 N 000 11 35.24 E 200.00  12.00  0.00 ");
        importer.readLine(
            ";SENSOR2: 100112 120300 SENSOR @A NULL 159.98 240.02 150.900 NULL \"hull sensor\" SUBJECT held on hull sensor");
        importer.readLine(
            ";SENSOR2: 100112 120300 SENSOR @A NULL 164.44 235.56 150.915 NULL \"tail sensor\" SUBJECT held on tail sensor");

        importer.storePendingSensors();

      }
      catch (IOException e)
      {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }

      return layers;
    }

    public void testUpdateBearings() throws ExecutionException
    {
      StackedDotHelper helper = new StackedDotHelper();
      TimeSeriesCollection dotPlotData = new TimeSeriesCollection();
      TimeSeriesCollection linePlotData = new TimeSeriesCollection();
      TrackDataProvider tracks = getTrackData();
      boolean onlyVis = false;
      boolean showCourse = true;
      boolean flipAxes = false;
      ErrorLogger logger = new LoggingService();
      boolean updateDoublets = true;
      TimeSeriesCollection targetCourseSeries = new TimeSeriesCollection();
      TimeSeriesCollection targetSpeedSeries = new TimeSeriesCollection();
      TimeSeriesCollection measuredValuesColl = new TimeSeriesCollection();
      TimeSeriesCollection ambigValuesColl = new TimeSeriesCollection();
      TimeSeries ownshipCourseSeries = new TimeSeries("OS Course");
      TimeSeries targetBearingSeries = new TimeSeries("Tgt Bearing");
      TimeSeries targetCalculatedSeries = new TimeSeries("target calc");
      ResidualXYItemRenderer overviewSpeedRenderer = null;
      WrappingResidualRenderer overviewCourseRenderer = null;
      SetBackgroundShade backShader = new SetBackgroundShade()
      {
        @Override
        public void setShade(Paint errorColor)
        {
        }
      };

      helper.initialise(tracks, true, onlyVis, logger, "Bearings", true, false);
      helper.updateBearingData(dotPlotData, linePlotData, tracks, onlyVis,
          showCourse, flipAxes, logger, updateDoublets, targetCourseSeries,
          targetSpeedSeries, measuredValuesColl, ambigValuesColl,
          ownshipCourseSeries, targetBearingSeries, targetCalculatedSeries,
          overviewSpeedRenderer, overviewCourseRenderer, backShader);

      // have a look at what's happened

      // error plot. the data is ambiguous, so we've got 4 sets of errors (two sensors, port & stbd)
      assertEquals("has error data", 4, dotPlotData.getSeriesCount());

      // note: even though TMA only has 9 fixes, we get 10 errors since we interpolate
      assertEquals("series correct length", 10, dotPlotData.getSeries(0)
          .getItemCount());
      assertEquals("series correct length", 9, dotPlotData.getSeries(1)
          .getItemCount());
      assertEquals("series correct length", 10, dotPlotData.getSeries(2)
          .getItemCount());
      assertEquals("series correct length", 9, dotPlotData.getSeries(3)
          .getItemCount());

      // note: even though TMA only has 9 fixes, we get 10 errors since we interpolate
      assertEquals("series correct name", "ERRORShull sensor", dotPlotData
          .getSeries(0).getKey());
      assertEquals("series correct name", "ERRORStail sensor", dotPlotData
          .getSeries(1).getKey());
      assertEquals("series correct name", "hull sensor", dotPlotData.getSeries(
          2).getKey());
      assertEquals("series correct name", "tail sensor", dotPlotData.getSeries(
          3).getKey());

      // error plot. the data is ambiguous, so we've got 4 sets of errors (two sensors, port & stbd)
      assertEquals("has error data", 6, linePlotData.getSeriesCount());

      // note: even though TMA only has 9 fixes, we get 10 errors since we interpolate
      assertEquals("series correct length", 10, linePlotData.getSeries(0)
          .getItemCount());
      assertEquals("series correct length", 9, linePlotData.getSeries(1)
          .getItemCount());
      assertEquals("series correct length", 10, linePlotData.getSeries(2)
          .getItemCount());
      assertEquals("series correct length", 9, linePlotData.getSeries(3)
          .getItemCount());
      assertEquals("series correct length", 10, linePlotData.getSeries(4)
          .getItemCount());
      assertEquals("series correct length", 9, linePlotData.getSeries(5)
          .getItemCount());

      // note: even though TMA only has 9 fixes, we get 10 errors since we interpolate
      assertEquals("series correct name", "M_hull sensor", linePlotData
          .getSeries(0).getKey());
      assertEquals("series correct name", "M_tail sensor", linePlotData
          .getSeries(1).getKey());
      assertEquals("series correct name", "hull sensor", linePlotData.getSeries(
          2).getKey());
      assertEquals("series correct name", "tail sensor", linePlotData.getSeries(
          3).getKey());
      assertEquals("series correct name", "Calculatedhull sensor", linePlotData
          .getSeries(4).getKey());
      assertEquals("series correct name", "Calculatedtail sensor", linePlotData
          .getSeries(5).getKey());
    }
  }

  public static class TestSlicing extends TestCase
  {
    public void testOSLegDetector()
    {
      final TimeSeries osC = new TimeSeries(new FixedMillisecond());
      long time = 0;
      osC.add(new FixedMillisecond(time++), 20d);
      osC.add(new FixedMillisecond(time++), 21d);
      osC.add(new FixedMillisecond(time++), 22d);
      osC.add(new FixedMillisecond(time++), 20d);
      osC.add(new FixedMillisecond(time++), 21d);
      osC.add(new FixedMillisecond(time++), 20d);

      assertFalse(containsIdenticalValues(osC, 3));

      // inject some more duplicates
      osC.add(new FixedMillisecond(time++), 20d);
      osC.add(new FixedMillisecond(time++), 20d);
      osC.add(new FixedMillisecond(time++), 20d);

      assertTrue(containsIdenticalValues(osC, 3));

      osC.clear();
      osC.add(new FixedMillisecond(time++), 20d);
      osC.add(new FixedMillisecond(time++), 21d);
      osC.add(new FixedMillisecond(time++), 21d);
      osC.add(new FixedMillisecond(time++), 20d);
      osC.add(new FixedMillisecond(time++), 20d);
      osC.add(new FixedMillisecond(time++), 20d);
      osC.add(new FixedMillisecond(time++), 21d);
      osC.add(new FixedMillisecond(time++), 21d);
      osC.add(new FixedMillisecond(time++), 20d);
      osC.add(new FixedMillisecond(time++), 20d);

      assertFalse("check we're verifying single runs of matches",
          containsIdenticalValues(osC, 3));

      osC.add(new FixedMillisecond(time++), 20d);
      osC.add(new FixedMillisecond(time++), 20d);

      assertTrue(containsIdenticalValues(osC, 3));
    }

    public void testSetLeg()
    {
      final TrackWrapper host = new TrackWrapper();
      host.setName("Host Track");

      // create a sensor
      final SensorWrapper sensor = new SensorWrapper("Sensor");
      sensor.setHost(host);
      host.add(sensor);

      // add some cuts
      final ArrayList<SensorContactWrapper> contacts =
          new ArrayList<SensorContactWrapper>();
      for (int i = 0; i < 30; i++)
      {
        final HiResDate thisDTG = new HiResDate(10000 * i);
        final WorldLocation thisLocation = new WorldLocation(2 + 0.01 * i, 2
            + 0.03 * i, 0);
        final SensorContactWrapper scw = new SensorContactWrapper(host
            .getName(), thisDTG, new WorldDistance(4, WorldDistance.MINUTES),
            25d, thisLocation, Color.RED, "" + i, 0, sensor.getName());
        sensor.add(scw);
        contacts.add(scw);

        // also create a host track fix at this DTG
        final Fix theFix = new Fix(thisDTG, thisLocation, 12d, 3d);
        final FixWrapper newF = new FixWrapper(theFix);
        host.add(newF);
      }

      // produce the target leg
      final TrackWrapper target = new TrackWrapper();
      target.setName("Tgt Track");

      // add a TMA leg
      final Layers theLayers = new Layers();
      theLayers.addThisLayer(host);
      theLayers.addThisLayer(target);

      final SensorContactWrapper[] contactArr = contacts.toArray(
          new SensorContactWrapper[]
          {});
      final RelativeTMASegment newLeg = new RelativeTMASegment(contactArr,
          new WorldVector(1, 1, 0), new WorldSpeed(12, WorldSpeed.Kts), 12d,
          theLayers, Color.red);
      target.add(newLeg);

      final BaseStackedDotsView view = new BaseStackedDotsView(true, false)
      {
        @Override
        protected boolean allowDisplayOfTargetOverview()
        {
          return false;
        }

        @Override
        protected boolean allowDisplayOfZoneChart()
        {
          return false;
        }

        @Override
        protected String formatValue(final double value)
        {
          return "" + value;
        }

        @Override
        protected ZoneSlicer getOwnshipZoneSlicer(final ColorProvider blueProv)
        {
          return null;
        }

        @Override
        protected String getType()
        {
          return null;
        }

        @Override
        protected String getUnits()
        {
          return null;
        }

        @Override
        protected void updateData(final boolean updateDoublets)
        {
          // no, nothing to do.
        }
      };

      // try to set a zone on the track
      Zone trimmedPeriod = new Zone(150000, 220000, Color.RED);
      view.setLeg(host, target, trimmedPeriod);

      // ok, check the leg has changed
      assertEquals("leg start changed", 150000, target.getStartDTG().getDate()
          .getTime());
      assertEquals("leg start changed", 220000, target.getEndDTG().getDate()
          .getTime());

      // ok, also see if we can create a new leg
      trimmedPeriod = new Zone(250000, 320000, Color.RED);
      view.setLeg(host, target, trimmedPeriod);

    }
  }

  public static final String MEASURED_DATASET = "Measured";

  public static final String CALCULATED_VALUES = "Calculated";

  /**
   * produce a color shade, according to whether the max error is inside 3 degrees or not.
   *
   * @param errorSeries
   * @return
   */
  private static Paint calculateErrorShadeFor(
      final TimeSeriesCollection errorSeries, final double cutOffValue)
  {
    final Paint col;
    double maxError = 0d;
    final TimeSeries ts = errorSeries.getSeries(0);
    final List<?> items = ts.getItems();
    for (final Iterator<?> iterator = items.iterator(); iterator.hasNext();)
    {
      final TimeSeriesDataItem item = (TimeSeriesDataItem) iterator.next();
      final boolean useMe;
      // check this isn't infill
      if (item instanceof ColouredDataItem)
      {
        final ColouredDataItem cd = (ColouredDataItem) item;
        useMe = cd.isShapeFilled();
      }
      else
      {
        useMe = true;
      }
      if (useMe)
      {
        final double thisE = (Double) item.getValue();
        maxError = Math.max(maxError, Math.abs(thisE));
      }
    }

    if (maxError > cutOffValue)
    {
      col = new Color(1f, 0f, 0f, 0.05f);
    }
    else
    {
      final float shade = (float) (0.03f + (cutOffValue - maxError) * 0.02f);
      col = new Color(0f, 1f, 0f, shade);
    }

    return col;
  }

  private static void clearPrivateListeners(final ISecondaryTrack targetTrack)
  {
    if (targetTrack instanceof TrackWrapper)
    {
      final TrackWrapper target = (TrackWrapper) targetTrack;

      // ok - we may have registered some interpolation listeners on the track
      // delete them if necessary
      final PropertyChangeListener[] list = target.getPropertyChangeListeners(
          PlainWrapper.LOCATION_CHANGED);
      for (final PropertyChangeListener t : list)
      {
        if (t instanceof PrivatePropertyChangeListener)
        {
          final PrivatePropertyChangeListener prop =
              (PrivatePropertyChangeListener) t;
          prop.detach();
        }
      }
    }
  }

  /**
   * determine if this time series contains many identical values - this is an indicator for data
   * coming from a simulator, for which turns can't be determined by our peak tracking algorithm.
   *
   * @param dataset
   * @return
   */
  private static boolean containsIdenticalValues(final TimeSeries dataset,
      final Integer NumMatches)
  {
    final int num = dataset.getItemCount();

    final int numMatches;
    if (NumMatches != null)
    {
      numMatches = NumMatches;
    }
    else
    {
      final double MATCH_PROPORTION = 0.1;
      numMatches = (int) (num * MATCH_PROPORTION);
    }

    double lastCourse = 0d;
    int matchCount = 0;

    for (int ctr = 0; ctr < num; ctr++)
    {
      final TimeSeriesDataItem thisItem = dataset.getDataItem(ctr);
      final double thisCourse = (Double) thisItem.getValue();
      if (thisCourse == lastCourse)
      {
        // ok, count the duplicates
        matchCount++;

        if (matchCount >= numMatches)
        {
          return true;
        }
      }
      else
      {
        matchCount = 0;
      }
      lastCourse = thisCourse;
    }

    return false;
  }

  private static void generateInterpolatedDoublet(final HiResDate requiredTime,
      final TargetDoublet doublet, final TrackSegment segment)
  {
    // ok, we'll interpolate the nearest value
    FixWrapper before = null;
    FixWrapper after = null;
    final Enumeration<Editable> fixes = segment.elements();
    while (fixes.hasMoreElements() && after == null)
    {
      final FixWrapper thisF = (FixWrapper) fixes.nextElement();

      final HiResDate thisTime = thisF.getDTG();

      if (before == null || thisTime.lessThan(requiredTime))
      {
        before = thisF;
      }
      else if (thisTime.greaterThanOrEqualTo(requiredTime))
      {
        after = thisF;
      }
    }

    // just check if we're on one of the values
    final FixWrapper toUse;
    if (before != null && before.getDTG().equals(requiredTime))
    {
      toUse = before;
    }
    else if (after != null && after.getDTG().equals(requiredTime))
    {
      toUse = after;
    }
    else
    {
      // ok, we've now boxed the required value
      toUse = FixWrapper.interpolateFix(before, after, requiredTime);

      final FixWrapper beforeF = before;
      final FixWrapper afterF = after;

      // note. the interpolated fix needs to move, if the segments moves
      final PropertyChangeListener newListener =
          new PrivatePropertyChangeListener(segment.getWrapper(),
              PlainWrapper.LOCATION_CHANGED)
          {
            @Override
            public void propertyChange(final PropertyChangeEvent evt)
            {
              final FixWrapper tmpFix = FixWrapper.interpolateFix(beforeF,
                  afterF, requiredTime);
              toUse.setLocation(tmpFix.getLocation());
            }
          };
      segment.getWrapper().addPropertyChangeListener(
          PlainWrapper.LOCATION_CHANGED, newListener);
    }

    doublet.targetFix = toUse;
    doublet.targetParent = segment;
  }

  /**
   * sort out data of interest
   *
   */
  public static TreeSet<Doublet> getDoublets(final TrackWrapper sensorHost,
      final ISecondaryTrack targetTrack, final boolean onlyVis,
      final boolean needBearing, final boolean needFrequency)
  {
    final TreeSet<Doublet> res = new TreeSet<Doublet>();

    // friendly fix-wrapper to save us repeatedly creating it
    final FixWrapper index = new FixWrapper(new Fix(null, new WorldLocation(0,
        0, 0), 0.0, 0.0));

    // note - we have to inject some listeners, so that
    // interpolated fixes know when their parent has updated.
    // each time we come in here, we delete existing ones,
    // as housekeeping.
    clearPrivateListeners(targetTrack);

    final Vector<TrackSegment> theSegments;
    if (targetTrack != null)
    {
      theSegments = getTargetLegs(targetTrack);
    }
    else
    {
      theSegments = null;
    }

    // loop through our sensor data
    final Enumeration<Editable> sensors = sensorHost.getSensors().elements();
    if (sensors != null)
    {
      while (sensors.hasMoreElements())
      {
        final SensorWrapper wrapper = (SensorWrapper) sensors.nextElement();
        if (!onlyVis || (onlyVis && wrapper.getVisible()))
        {
          final Enumeration<Editable> cuts = wrapper.elements();

          while (cuts.hasMoreElements())
          {
            final SensorContactWrapper scw = (SensorContactWrapper) cuts
                .nextElement();

            if (!onlyVis || (onlyVis && scw.getVisible()))
            {
              // is this cut suitable for what we're looking for?
              if (needBearing)
              {
                if (!scw.getHasBearing())
                {
                  continue;
                }
              }

              // aaah, but does it meet the frequency requirement?
              if (needFrequency)
              {
                if (!scw.getHasFrequency())
                {
                  continue;
                }
              }

              /**
               * note: if this is frequency data then we accept an interpolated fix. This is based
               * upon the working practice that initially legs of target track are created from
               * bearing data.
               *
               * Plus, there is greater variance in bearing angle - so it's more important to get
               * the right data item.
               */

              /**
               * Note: CANCEL THE ABOVE. Since the contact is travelling in a straight, on steady
               * speed when on a leg, it's perfectly OK to interpolate a target position for any
               * sensor time.
               */
              final boolean interpFix = true;// needFrequency;

              /**
               * for frequency data we don't generate a double for dynamic infills, since we have
               * low confidence in the target course/speed
               */
              final boolean allowInfill = !needFrequency;

              final TargetDoublet doublet = getTargetDoublet(index, theSegments,
                  scw.getDTG(), interpFix, allowInfill);

              final Doublet thisDub;
              final FixWrapper hostFix;

              final Watchable[] matches = sensorHost.getNearestTo(scw.getDTG());
              if (matches != null && matches.length == 1)
              {
                hostFix = (FixWrapper) matches[0];
              }
              else
              {
                hostFix = null;
              }

              if (doublet.targetFix != null && hostFix != null)
              {
                thisDub = new Doublet(scw, doublet.targetFix,
                    doublet.targetParent, hostFix);

                // if we've no target track add all the points
                if (targetTrack == null)
                {
                  // store our data
                  res.add(thisDub);
                }
                else
                {
                  // if we've got a target track we only add points
                  // for which we
                  // have
                  // a target location
                  if (doublet.targetFix != null)
                  {
                    // store our data
                    res.add(thisDub);
                  }
                } // if we know the track
                // if there are any matching items

              } // if we find a match
              // this test used to be the following, but we changed it so we
              // could see measured data even when we don't have track:
              // else if ((targetTrack == null && hostFix != null) || (doublet.targetFix == null &&
              // hostFix != null))
              else if (hostFix != null && (doublet.targetFix == null
                  || targetTrack == null))
              {
                // no target data, just use ownship sensor data
                thisDub = new Doublet(scw, null, null, hostFix);
                res.add(thisDub);
              }
            } // if cut is visible
          } // loop through cuts
        } // if sensor is visible
      } // loop through sensors
    } // if there are sensors

    return res;
  }

  /**
   *
   * @param workingFix
   *          pre-existing fix object, to stop us repeatedly creating it
   * @param theSegments
   *          the segment within this track
   * @param requiredTime
   *          the time we need data for
   * @param interpFix
   *          whether to only accept a target fix within 1 second of the target time, or to
   *          interpolate the nearest one
   * @param allowInfill
   *          whether we generate a doublet for dynamic infill segments
   * @return a Doublet containing the relevant data
   */
  private static TargetDoublet getTargetDoublet(final FixWrapper workingFix,
      final Vector<TrackSegment> theSegments, final HiResDate requiredTime,
      final boolean interpFix, final boolean allowInfill)
  {
    final TargetDoublet doublet = new TargetDoublet();
    if (theSegments != null && !theSegments.isEmpty())
    {
      final Iterator<TrackSegment> iter = theSegments.iterator();
      while (iter.hasNext())
      {
        final TrackSegment ts = iter.next();

        if (ts.endDTG() == null || ts.startDTG() == null)
        {
          // ok, move onto the next segment
          CorePlugin.logError(IStatus.WARNING,
              "Warning, segment is missing data:" + ts, null);
          continue;
        }

        final TimePeriod validPeriod = new TimePeriod.BaseTimePeriod(ts
            .startDTG(), ts.endDTG());
        if (validPeriod.contains(requiredTime))
        {

          // if this is an infill, then we're relaxed about the errors
          if (ts instanceof DynamicInfillSegment)
          {
            // aaah, but are we interested in infill segments?
            if (allowInfill)
            {
              handleDynamicInfill(workingFix, requiredTime, doublet, ts);
            }
          }
          else
          {
            // see if we're allowing an interpolated fix
            if (interpFix)
            {
              generateInterpolatedDoublet(requiredTime, doublet, ts);
              break;
            }
            else
            {
              // ok, check we have a TMA fix almost exactly at this time
              final Enumeration<Editable> fixes = ts.elements();
              while (fixes.hasMoreElements())
              {
                final FixWrapper thisF = (FixWrapper) fixes.nextElement();

                // note: workaround. When we've merged the track,
                // the new legs are actually one millisecond later.
                // workaround this.
                final long timeDiffMicros = Math.abs(thisF.getDTG().getMicros()
                    - requiredTime.getMicros());

                if (timeDiffMicros <= 1000)
                {
                  // sorted. here we go
                  doublet.targetParent = ts;

                  doublet.targetFix = thisF;

                  // ok, done.
                  break;
                }
              }
            }
          }
        }
      }
    }

    return doublet;
  }

  private static Vector<TrackSegment> getTargetLegs(
      final ISecondaryTrack targetTrack)
  {
    final Vector<TrackSegment> _theSegments = new Vector<TrackSegment>();
    final Enumeration<Editable> trkData = targetTrack.segments();

    while (trkData.hasMoreElements())
    {
      final Editable thisI = trkData.nextElement();
      if (thisI instanceof SegmentList)
      {
        final SegmentList thisList = (SegmentList) thisI;
        final Enumeration<Editable> theElements = thisList.elements();
        while (theElements.hasMoreElements())
        {
          final TrackSegment ts = (TrackSegment) theElements.nextElement();
          if (ts.getVisible())
          {
            _theSegments.add(ts);
          }
        }

      }
      else if (thisI instanceof TrackSegment)
      {
        final TrackSegment ts = (TrackSegment) thisI;
        _theSegments.add(ts);
      }
    }
    return _theSegments;
  }

  private static void handleDynamicInfill(final FixWrapper workingFix,
      final HiResDate requiredTime, final TargetDoublet doublet,
      final TrackSegment segment)
  {
    // sorted. here we go
    doublet.targetParent = segment;

    // create an object with the right time
    workingFix.getFix().setTime(requiredTime);

    // and find any matching items
    final SortedSet<Editable> items = segment.tailSet(workingFix);
    if (!items.isEmpty())
    {
      doublet.targetFix = (FixWrapper) items.first();
    }
  }

  public static ArrayList<Zone> sliceOwnship(final TimeSeries osCourse,
      final ZoneChart.ColorProvider colorProvider)
  {
    // make a decision on which ownship slicer to use
    final IOwnshipLegDetector detector;
    if (containsIdenticalValues(osCourse, null))
    {
      detector = new ArtificalLegDetector();
    }
    else
    {
      detector = new PeakTrackingOwnshipLegDetector();
    }

    final int num = osCourse.getItemCount();
    final long[] times = new long[num];
    final double[] speeds = new double[num];
    final double[] courses = new double[num];

    for (int ctr = 0; ctr < num; ctr++)
    {
      final TimeSeriesDataItem thisItem = osCourse.getDataItem(ctr);
      final FixedMillisecond thisM = (FixedMillisecond) thisItem.getPeriod();
      times[ctr] = thisM.getMiddleMillisecond();
      speeds[ctr] = 0;
      courses[ctr] = (Double) thisItem.getValue();
    }
    final List<LegOfData> legs = detector.identifyOwnshipLegs(times, speeds,
        courses, 5, Precision.LOW);
    final ArrayList<Zone> res = new ArrayList<Zone>();

    for (final LegOfData leg : legs)
    {
      final Zone newZone = new Zone(leg.getStart(), leg.getEnd(), colorProvider
          .getZoneColor());
      res.add(newZone);
    }

    return res;
  }

  /**
   * the maximum number of items we plot as symbols. Above this we just use a line
   */
  private final int MAX_ITEMS_TO_PLOT = 1000;

  /**
   * the track being dragged
   */
  private TrackWrapper _primaryTrack;

  /**
   * the secondary track we're monitoring
   */
  private ISecondaryTrack _secondaryTrack;

  /**
   * the set of points to watch on the primary track. This is stored as a sorted set because if we
   * have multiple sensors they may be suppled in chronological order, or they may represent
   * overlapping time periods
   */
  private TreeSet<Doublet> _primaryDoublets;

  public List<SensorContactWrapper> getBearings(final TrackWrapper primaryTrack,
      final boolean onlyVis, final TimePeriod targetPeriod)
  {
    final List<SensorContactWrapper> res =
        new ArrayList<SensorContactWrapper>();

    // loop through our sensor data
    final Enumeration<Editable> sensors = primaryTrack.getSensors().elements();
    if (sensors != null)
    {
      while (sensors.hasMoreElements())
      {
        final SensorWrapper wrapper = (SensorWrapper) sensors.nextElement();
        if (!onlyVis || (onlyVis && wrapper.getVisible()))
        {
          final Enumeration<Editable> cuts = wrapper.elements();
          while (cuts.hasMoreElements())
          {
            final SensorContactWrapper scw = (SensorContactWrapper) cuts
                .nextElement();
            if (!onlyVis || (onlyVis && scw.getVisible()))
            {
              if (targetPeriod == null || targetPeriod.contains(scw.getDTG()))
              {
                res.add(scw);
              }
              // if we find a match
            } // if cut is visible
          } // loop through cuts
        } // if sensor is visible
      } // loop through sensors
    } // if there are sensors

    return res;
  }

  public TreeSet<Doublet> getDoublets(final boolean onlyVis,
      final boolean needBearing, final boolean needFrequency)
  {
    return getDoublets(_primaryTrack, _secondaryTrack, onlyVis, needBearing,
        needFrequency);
  }

  public TrackWrapper getPrimaryTrack()
  {
    return _primaryTrack;
  }

  public ISecondaryTrack getSecondaryTrack()
  {
    return _secondaryTrack;
  }

  /**
   * initialise the data, check we've got sensor data & the correct number of visible tracks
   *
   * @param showError
   * @param onlyVis
   * @param holder
   */
  void initialise(final TrackDataProvider tracks, final boolean showError,
      final boolean onlyVis, final ErrorLogger logger, final String dataType,
      final boolean needBrg, final boolean needFreq)
  {

    _secondaryTrack = null;
    _primaryTrack = null;

    // do we have some data?
    if (tracks == null)
    {
      // output error message
      logger.logError(IStatus.INFO, "Please open a Debrief plot", null);
      return;
    }

    // check we have a primary track
    final WatchableList priTrk = tracks.getPrimaryTrack();
    if (priTrk == null)
    {
      logger.logError(IStatus.INFO,
          "A primary track must be placed on the Tote", null);
      return;
    }
    else
    {
      if (!(priTrk instanceof TrackWrapper))
      {
        logger.logError(IStatus.INFO,
            "The primary track must be a vehicle track", null);
        return;
      }
      else
      {
        _primaryTrack = (TrackWrapper) priTrk;
      }
    }

    // now the sec track
    final WatchableList[] secs = tracks.getSecondaryTracks();

    // any?
    if ((secs == null) || (secs.length == 0))
    {
      logger.logError(IStatus.INFO, "No secondary track assigned", null);
      return;
    }
    else
    {
      // too many?
      if (secs.length > 1)
      {
        logger.logError(IStatus.INFO,
            "Only 1 secondary track may be on the tote", null);
        return;
      }

      // correct sort?
      final WatchableList secTrk = secs[0];
      if (!(secTrk instanceof ISecondaryTrack))
      {
        logger.logError(IStatus.INFO,
            "The secondary track must be a vehicle track", null);
        return;
      }
      else
      {
        _secondaryTrack = (ISecondaryTrack) secTrk;
      }
    }

    // must have worked, hooray
    logger.logError(IStatus.OK, null, null);

    // ok, get the positions
    updateDoublets(onlyVis, needBrg, needFreq);

  }

  /**
   * clear our data, all is finished
   */
  public void reset()
  {
    if (_primaryDoublets != null)
    {
      _primaryDoublets.clear();
    }
    _primaryDoublets = null;
    _primaryTrack = null;
    _secondaryTrack = null;
  }

  /**
   * is this a multi-sensor dataset?
   * 
   * @param doublets
   * @return
   */
  private final static boolean isMultiSensor(final TreeSet<Doublet> doublets)
  {

    final Iterator<Doublet> iter = doublets.iterator();
    SensorWrapper lastS = null;
    while (iter.hasNext())
    {
      final Doublet next = iter.next();
      final SensorWrapper thisS = next.getSensorCut().getSensor();
      if (lastS == null)
      {
        lastS = thisS;
      }
      else if (!lastS.equals(thisS))
      {
        return true;
      }
    }
    return false;
  }

  /**
   * ok, our track has been dragged, calculate the new series of offsets
   *
   * @param linePlot
   * @param dotPlot
   * @param onlyVis
   * @param showCourse
   * @param b
   * @param holder
   * @param logger
   * @param targetCourseSeries
   * @param targetSpeedSeries
   * @param ownshipCourseSeries
   * @param targetBearingSeries
   * @param overviewSpeedRenderer
   * @param _overviewCourseRenderer
   *
   * @param currentOffset
   *          how far the current track has been dragged
   */
  public void updateBearingData(final TimeSeriesCollection dotPlotData,
      final TimeSeriesCollection linePlotData, final TrackDataProvider tracks,
      final boolean onlyVis, final boolean showCourse, final boolean flipAxes,
      final ErrorLogger logger, final boolean updateDoublets,
      final TimeSeriesCollection targetCourseSeries,
      final TimeSeriesCollection targetSpeedSeries,
      final TimeSeriesCollection measuredValuesColl,
      final TimeSeriesCollection ambigValuesColl,
      final TimeSeries ownshipCourseSeries,
      final TimeSeries targetBearingSeries,
      final TimeSeries targetCalculatedSeries,
      final ResidualXYItemRenderer overviewSpeedRenderer,
      final WrappingResidualRenderer overviewCourseRenderer,
      final SetBackgroundShade backShader)
  {
    // do we even have a primary track
    if (_primaryTrack == null)
    {
      // ok, clear the data
      linePlotData.removeAllSeries();
      dotPlotData.removeAllSeries();
      targetCourseSeries.removeAllSeries();
      targetSpeedSeries.removeAllSeries();
      return;
    }

    // ok, find the track wrappers
    if (_secondaryTrack == null)
    {
      initialise(tracks, false, onlyVis, logger, "Bearing", true, false);
    }

    // did it work?
    // if (_secondaryTrack == null)
    // return;

    // ok - the tracks have moved. better update the doublets
    if (updateDoublets)
    {
      updateDoublets(onlyVis, true, false);
    }

    // aah - but what if we've ditched our doublets?
    if ((_primaryDoublets == null) || (_primaryDoublets.size() == 0))
    {
      // better clear the plot
      dotPlotData.removeAllSeries();
      linePlotData.removeAllSeries();
      return;
    }

    // check if we've got multi sensor
    final boolean multiSensor = isMultiSensor(_primaryDoublets);

    // create the collection of series
    final TimeSeriesCollection calculatedSeries = new TimeSeriesCollection();

    final TimeSeriesCollection ambigErrorSeries = new TimeSeriesCollection();

    // the previous steps occupy some time.
    // just check we haven't lost the primary track while they were running
    if (_primaryTrack == null)
    {
      return;
    }

    // produce a dataset for each track
    final TimeSeries osCourseValues = new TimeSeries("O/S Course");
    final TimeSeries tgtCourseValues = new TimeSeries("Tgt Course");
    final TimeSeries tgtSpeedValues = new TimeSeries("Tgt Speed");
    final TimeSeries allCuts = new TimeSeries("Sensor cuts");

    // createa list of series, so we can pause their updates
    final List<TimeSeries> sList = new Vector<TimeSeries>();
    sList.add(osCourseValues);
    sList.add(tgtCourseValues);
    sList.add(tgtSpeedValues);
    sList.add(allCuts);
    sList.add(targetCalculatedSeries);
    sList.add(targetBearingSeries);
    sList.add(ownshipCourseSeries);

    final List<TimeSeriesCollection> tList = new Vector<TimeSeriesCollection>();
    tList.add(measuredValuesColl);
    tList.add(targetCourseSeries);
    tList.add(targetSpeedSeries);
    tList.add(dotPlotData);
    tList.add(linePlotData);
    tList.add(calculatedSeries);
    tList.add(ambigErrorSeries);
    tList.add(ambigValuesColl);

    // ok, wrap the switching on/off of notify in try/catch,
    // to be sure to switch notify back on at end
    try
    {
      // now switch off updates
      for (final TimeSeriesCollection series : tList)
      {
        series.setNotify(false);

        series.removeAllSeries();
      }
      for (final TimeSeries series : sList)
      {
        series.setNotify(false);

        // and clear the list
        series.clear();
      }

      // create the color for resolved ambig data
      final Color grayShade = new Color(155, 155, 155, 50);

      // ok, run through the points on the primary track
      final Iterator<Doublet> iter = _primaryDoublets.iterator();
      while (iter.hasNext())
      {
        final Doublet thisD = iter.next();

        final boolean parentIsNotDynamic = thisD.getTargetTrack() == null
            || !(thisD.getTargetTrack() instanceof DynamicInfillSegment);
        try
        {
          // obvious stuff first (stuff that doesn't need the tgt data)
          final Color thisColor = thisD.getColor();
          double measuredBearing = thisD.getMeasuredBearing();
          double ambigBearing = thisD.getAmbiguousMeasuredBearing();
          final HiResDate currentTime = thisD.getDTG();
          final FixedMillisecond thisMilli = new FixedMillisecond(currentTime
              .getDate().getTime());

          final boolean hasAmbiguous = !Double.isNaN(ambigBearing);

          // ok, we need to make the color darker if it's starboard
          final boolean bearingToPort = thisD.getSensorCut().isBearingToPort();

          // make the color darker, if it's to stbg
          final Color bearingColor;
          if (bearingToPort)
          {
            bearingColor = thisColor;
          }
          else
          {
            bearingColor = thisColor.darker();
          }

          // put the measured bearing back in the positive domain
          if (measuredBearing < 0)
          {
            measuredBearing += 360d;
          }

          // stop, stop, stop - do we wish to plot bearings in the +/- 180 domain?
          if (flipAxes)
          {
            if (measuredBearing > 180)
            {
              measuredBearing -= 360;
            }
          }

          final ColouredDataItem mBearing = new ColouredDataItem(thisMilli,
              measuredBearing, bearingColor, false, null, true,
              parentIsNotDynamic, thisD.getSensorCut());

          // find the series for this sensor
          final SensorWrapper sensor = thisD.getSensorCut().getSensor();
          final String seriesName = multiSensor
              ? BaseStackedDotsView.MEASURED_VALUES + sensor.getName()
              : BaseStackedDotsView.MEASURED_VALUES;
          TimeSeries measuredBearings = measuredValuesColl.getSeries(
              seriesName);
          if (measuredBearings == null)
          {
            measuredBearings = new TimeSeries(seriesName);
            measuredValuesColl.addSeries(measuredBearings);
          }

          // and add them to the series
          measuredBearings.add(mBearing);

          if (hasAmbiguous)
          {
            // put the ambig baering into the correct domain
            while (ambigBearing < 0)
            {
              ambigBearing += 360;
            }

            if (flipAxes && ambigBearing > 180)
            {
              ambigBearing -= 360;
            }

            // make the color darker, if we're on the stbd bearnig
            final Color ambigColor;
            if (bearingToPort)
            {
              ambigColor = thisColor.darker();
            }
            else
            {
              ambigColor = thisColor;
            }

            // if this cut has been resolved, we don't show a symbol
            // for the ambiguous cut
            final boolean showSymbol = true;
            final Color color = thisD.getHasBeenResolved() ? grayShade
                : ambigColor;

            final ColouredDataItem amBearing = new ColouredDataItem(thisMilli,
                ambigBearing, color, false, null, showSymbol,
                parentIsNotDynamic, thisD.getSensorCut());
            TimeSeries ambigValues = ambigValuesColl.getSeries(sensor
                .getName());
            if (ambigValues == null)
            {
              ambigValues = new TimeSeries(sensor.getName());
              ambigValuesColl.addSeries(ambigValues);
            }
            ambigValues.add(amBearing);
          }

          // do we have target data?
          if (thisD.getTarget() != null)
          {
            // and has this target fix know it's location?
            // (it may not, if it's a relative leg that has been extended)
            if (thisD.getTarget().getFixLocation() != null)
            {
              double calculatedBearing = thisD.getCalculatedBearing(null, null);

              // note: now that we're allowing multi-sensor TMA, we should color the
              // errors acccording to the sensor color (not the target color)
              final Color error = thisD.getColor();
              final Color calcColor = thisD.getTarget().getColor();
              final double thisTrueError = thisD.calculateBearingError(
                  measuredBearing, calculatedBearing);

              if (flipAxes)
              {
                if (calculatedBearing > 180)
                {
                  calculatedBearing -= 360;
                }
              }
              else
              {
                if (calculatedBearing < 0)
                {
                  calculatedBearing += 360;
                }
              }

              final Color brgColor;
              if (bearingToPort)
              {
                brgColor = error;
              }
              else
              {
                brgColor = error.darker();
              }

              final ColouredDataItem newTrueError = new ColouredDataItem(
                  thisMilli, thisTrueError, brgColor, false, null, true,
                  parentIsNotDynamic, thisD.getTarget());

              final Color halfBearing = halfWayColor(calcColor, brgColor);

              final ColouredDataItem cBearing = new ColouredDataItem(thisMilli,
                  calculatedBearing, halfBearing, true, null, true,
                  parentIsNotDynamic, thisD.getTarget());

              final String sensorName = thisD.getSensorCut().getSensorName();

              // ok, get this error
              final String errorName = multiSensor
                  ? BaseStackedDotsView.ERROR_VALUES + sensorName
                  : BaseStackedDotsView.ERROR_VALUES;
              TimeSeries thisError = dotPlotData.getSeries(errorName);
              if (thisError == null)
              {
                thisError = new TimeSeries(errorName);
                dotPlotData.addSeries(thisError);
              }

              thisError.add(newTrueError);

              // get the calc series for this one
              final String calcName = multiSensor
                  ? StackedDotHelper.CALCULATED_VALUES + sensorName
                  : StackedDotHelper.CALCULATED_VALUES;
              TimeSeries calculatedValues = calculatedSeries.getSeries(
                  calcName);
              if (calculatedValues == null)
              {
                calculatedValues = new TimeSeries(calcName);
                calculatedSeries.addSeries(calculatedValues);
              }

              calculatedValues.add(cBearing);

              // and the ambiguous error, if it hasn't been resolved
              if (!thisD.getHasBeenResolved())
              {
                if (flipAxes)
                {
                  if (ambigBearing > 180)
                  {
                    ambigBearing -= 360;
                  }
                }

                final Color ambigColor;
                if (bearingToPort)
                {
                  ambigColor = error.darker();
                }
                else
                {
                  ambigColor = error;
                }

                final double thisAmnigError = thisD.calculateBearingError(
                    ambigBearing, calculatedBearing);
                final ColouredDataItem newAmbigError = new ColouredDataItem(
                    thisMilli, thisAmnigError, ambigColor, false, null, true,
                    parentIsNotDynamic);

                TimeSeries ambigErrorValues = ambigErrorSeries.getSeries(
                    sensorName);
                if (ambigErrorValues == null)
                {
                  ambigErrorValues = new TimeSeries(sensorName);
                  ambigErrorSeries.addSeries(ambigErrorValues);
                }

                ambigErrorValues.add(newAmbigError);
              }

            }
          }

        }
        catch (final SeriesException e)
        {
          CorePlugin.logError(IStatus.INFO,
              "some kind of trip whilst updating bearing plot", e);
        }

      }

      // just double-check we've still got our primary doublets
      if (_primaryDoublets == null)
      {
        CorePlugin.logError(IStatus.WARNING,
            "FOR SOME REASON PRIMARY DOUBLETS IS NULL - INVESTIGATE", null);
        return;
      }

      if (_primaryDoublets.size() == 0)
      {
        CorePlugin.logError(IStatus.WARNING,
            "FOR SOME REASON PRIMARY DOUBLETS IS ZERO LENGTH - INVESTIGATE",
            null);
        return;
      }

      // right, we do course in a special way, since it isn't dependent on the
      // target track. Do course here.
      final HiResDate startDTG = _primaryDoublets.first().getDTG();
      final HiResDate endDTG = _primaryDoublets.last().getDTG();

      if (startDTG.greaterThan(endDTG))
      {
        System.err.println("in the wrong order, start:" + startDTG + " end:"
            + endDTG);
        return;
      }

      // special case - if the primary track is a single location
      if (_primaryTrack.isSinglePointTrack())
      {
        // ok, it's a single point. We'll use the sensor cut times for the course data

        // get the single location
        final FixWrapper loc = (FixWrapper) _primaryTrack.getPositionIterator()
            .nextElement();

        final Enumeration<Editable> segments = _secondaryTrack.segments();
        while (segments.hasMoreElements())
        {
          final Editable nextE = segments.nextElement();
          // if there's just one segment - then we need to wrap it
          final SegmentList segList;
          if (nextE instanceof SegmentList)
          {
            segList = (SegmentList) nextE;
          }
          else
          {
            segList = new SegmentList();
            segList.addSegment((TrackSegment) nextE);
          }

          final Enumeration<Editable> segIter = segList.elements();
          while (segIter.hasMoreElements())
          {
            final TrackSegment segment = (TrackSegment) segIter.nextElement();

            final Enumeration<Editable> enumer = segment.elements();
            while (enumer.hasMoreElements())
            {
              final FixWrapper thisTgtFix = (FixWrapper) enumer.nextElement();

              double ownshipCourse = MWC.Algorithms.Conversions.Rads2Degs(loc
                  .getCourse());

              // stop, stop, stop - do we wish to plot bearings in the +/- 180 domain?
              if (flipAxes && ownshipCourse > 180)
              {
                ownshipCourse -= 360;
              }
              final FixedMillisecond thisMilli = new FixedMillisecond(thisTgtFix
                  .getDateTimeGroup().getDate().getTime());
              final ColouredDataItem crseBearing = new ColouredDataItem(
                  thisMilli, ownshipCourse, loc.getColor(), true, null, true,
                  true);
              osCourseValues.addOrUpdate(crseBearing);
            }
          }
        }
      }
      else
      {

        // loop through using the iterator
        final Enumeration<Editable> pIter = _primaryTrack.getPositionIterator();
        final TimePeriod validPeriod = new TimePeriod.BaseTimePeriod(startDTG,
            endDTG);
        final List<Editable> validItems = new LinkedList<Editable>();
        while (pIter.hasMoreElements())
        {
          final FixWrapper fw = (FixWrapper) pIter.nextElement();
          if (validPeriod.contains(fw.getDateTimeGroup()))
          {
            validItems.add(fw);
          }
          else
          {
            // have we passed the end of the requested period?
            if (fw.getDateTimeGroup().greaterThan(endDTG))
            {
              // ok, drop out
              break;
            }
          }
        }

        // ok, now go through the list
        final Iterator<Editable> vIter = validItems.iterator();
        final int freq = Math.max(1, validItems.size() / MAX_ITEMS_TO_PLOT);
        int ctr = 0;
        while (vIter.hasNext())
        {
          final Editable ed = vIter.next();
          if (ctr++ % freq == 0)
          {
            final FixWrapper fw = (FixWrapper) ed;
            final FixedMillisecond thisMilli = new FixedMillisecond(fw
                .getDateTimeGroup().getDate().getTime());
            double ownshipCourse = MWC.Algorithms.Conversions.Rads2Degs(fw
                .getCourse());

            // stop, stop, stop - do we wish to plot bearings in the +/- 180 domain?
            if (flipAxes && ownshipCourse > 180)
            {
              ownshipCourse -= 360;
            }

            final ColouredDataItem crseBearing = new ColouredDataItem(thisMilli,
                ownshipCourse, fw.getColor(), true, null, true, true);
            osCourseValues.add(crseBearing);
          }
        }

      }

      if (_secondaryTrack != null)
      {
        // sort out the target course/speed
        final Enumeration<Editable> segments = _secondaryTrack.segments();
        final TimePeriod period = new TimePeriod.BaseTimePeriod(startDTG,
            endDTG);
        while (segments.hasMoreElements())
        {
          final Editable nextE = segments.nextElement();
          // if there's just one segment - then we need to wrap it
          final SegmentList segList;
          if (nextE instanceof SegmentList)
          {
            segList = (SegmentList) nextE;
          }
          else
          {
            segList = new SegmentList();
            // note: we can only set the wrapper
            // if we're looking at a real TMA solution
            if (_secondaryTrack instanceof TrackWrapper)
            {
              segList.setWrapper((TrackWrapper) _secondaryTrack);
            }

            // ok, add this segment to the list
            segList.addSegment((TrackSegment) nextE);
          }

          final Enumeration<Editable> segIter = segList.elements();
          while (segIter.hasMoreElements())
          {
            final TrackSegment segment = (TrackSegment) segIter.nextElement();

            // is this an infill segment
            final boolean isInfill = segment instanceof DynamicInfillSegment;

            // check it has values, and is in range
            if (segment.isEmpty() || segment.startDTG().greaterThan(endDTG)
                || segment.endDTG().lessThan(startDTG))
            {
              // ok, we can skip this one
            }
            else
            {
              final Enumeration<Editable> points = segment.elements();
              Double lastCourse = null;
              while (points.hasMoreElements())
              {
                final FixWrapper fw = (FixWrapper) points.nextElement();
                if (period.contains(fw.getDateTimeGroup()))
                {
                  // ok, create a point for it
                  final FixedMillisecond thisMilli = new FixedMillisecond(fw
                      .getDateTimeGroup().getDate().getTime());

                  double tgtCourse = MWC.Algorithms.Conversions.Rads2Degs(fw
                      .getCourse());
                  final double tgtSpeed = fw.getSpeed();

                  // see if we need to change the domain of the course to match
                  // the previous value
                  if (lastCourse != null)
                  {
                    if (tgtCourse - lastCourse > 190)
                    {
                      tgtCourse = tgtCourse - 360;
                    }
                    else if (tgtCourse - lastCourse < -180)
                    {
                      tgtCourse = 360 + tgtCourse;
                    }
                  }
                  lastCourse = tgtCourse;

                  // trim to +/- domain if we're flipping axes
                  if (flipAxes && tgtCourse > 180)
                  {
                    tgtCourse -= 360;
                  }

                  // we use the raw color for infills, to help find which
                  // infill we're referring to (esp in random infills)
                  final Color courseColor;
                  final Color speedColor;
                  if (isInfill)
                  {
                    courseColor = fw.getColor();
                    speedColor = fw.getColor();
                  }
                  else
                  {
                    courseColor = fw.getColor().brighter();
                    speedColor = fw.getColor().darker();
                  }

                  final ColouredDataItem crseBearingItem = new ColouredDataItem(
                      thisMilli, tgtCourse, courseColor, isInfill, null, true,
                      true);
                  tgtCourseValues.add(crseBearingItem);
                  final ColouredDataItem tgtSpeedItem = new ColouredDataItem(
                      thisMilli, tgtSpeed, speedColor, isInfill, null, true,
                      true);
                  tgtSpeedValues.add(tgtSpeedItem);
                }
              }
            }
          }
        }
      }

      // sort out the sensor cuts (all of them, not just those when we have target legs)
      final TimePeriod sensorPeriod;
      if (_secondaryTrack != null)
      {
        sensorPeriod = new TimePeriod.BaseTimePeriod(_secondaryTrack
            .getStartDTG(), _secondaryTrack.getEndDTG());
      }
      else
      {
        sensorPeriod = null;
      }
      final List<SensorContactWrapper> theBearings = getBearings(_primaryTrack,
          onlyVis, sensorPeriod);
      for (final SensorContactWrapper cut : theBearings)
      {
        double theBearing;

        // ensure it's in the positive domain
        if (cut.getBearing() < 0)
        {
          theBearing = cut.getBearing() + 360;
        }
        else
        {
          theBearing = cut.getBearing();
        }

        // put in the correct domain, if necessary
        if (flipAxes)
        {
          if (theBearing > 180d)
          {
            theBearing -= 360d;
          }
        }
        else
        {
          if (theBearing < 0)
          {
            theBearing += 360;
          }
        }

        // ok, store it.
        allCuts.addOrUpdate(new TimeSeriesDataItem(new FixedMillisecond(cut
            .getDTG().getDate().getTime()), theBearing));
      }

      // ok, add these new series
      // if (errorValues.getItemCount() > 0)
      // {
      // errorSeries.addSeries(errorValues);
      // }

      final Iterator<?> eIter = ambigErrorSeries.getSeries().iterator();
      while (eIter.hasNext())
      {
        final TimeSeries series = (TimeSeries) eIter.next();
        dotPlotData.addSeries(series);
      }

      final Iterator<?> mIter = measuredValuesColl.getSeries().iterator();
      while (mIter.hasNext())
      {
        final TimeSeries series = (TimeSeries) mIter.next();
        linePlotData.addSeries(series);
      }

      final Iterator<?> aIter = ambigValuesColl.getSeries().iterator();
      while (aIter.hasNext())
      {
        final TimeSeries series = (TimeSeries) aIter.next();
        linePlotData.addSeries(series);
      }

      final Iterator<?> cIter = calculatedSeries.getSeries().iterator();
      while (cIter.hasNext())
      {
        final TimeSeries series = (TimeSeries) cIter.next();
        linePlotData.addSeries(series);
      }

      if (tgtCourseValues.getItemCount() > 0)
      {
        targetCourseSeries.addSeries(tgtCourseValues);

        // ok, sort out the renderer
        if (overviewCourseRenderer != null)
        {
          overviewCourseRenderer.setLightweightMode(tgtCourseValues
              .getItemCount() > MAX_ITEMS_TO_PLOT);
        }
      }

      if (tgtSpeedValues.getItemCount() > 0)
      {
        targetSpeedSeries.addSeries(tgtSpeedValues);

        if (overviewSpeedRenderer != null)
        {
          overviewSpeedRenderer.setLightweightMode(tgtSpeedValues
              .getItemCount() > MAX_ITEMS_TO_PLOT);
        }
      }

      if (showCourse)
      {
        targetCourseSeries.addSeries(osCourseValues);
      }

      final Iterator<?> cIter2 = calculatedSeries.getSeries().iterator();
      while (cIter2.hasNext())
      {
        final TimeSeries series = (TimeSeries) cIter2.next();
        targetCalculatedSeries.addAndOrUpdate(series);
      }

      // and the course data for the zone chart
      if (!osCourseValues.isEmpty())
      {
        if (ownshipCourseSeries != null)
        {
          // is it currently empty?
          if (ownshipCourseSeries.isEmpty())
          {
            ownshipCourseSeries.addAndOrUpdate(osCourseValues);
          }
          else
          {
            // ok, ignore it. we only assign the data in the first pass
          }
        }
      }

      // and the bearing data for the zone chart
      if (!allCuts.isEmpty())
      {
        if (targetBearingSeries != null)
        {
          // is it currently empty?
          if (targetBearingSeries.isEmpty())
          {
            targetBearingSeries.addAndOrUpdate(allCuts);
          }
          else
          {
            // ok, ignore it. we only assign the data in the first pass
          }
        }
      }

      // find the color for maximum value in the error series, if we have error data
      if (dotPlotData.getSeriesCount() > 0)
      {
        // retrieve the cut-off value
        final double cutOffValue;
        final String prefValue = Application.getThisProperty(
            RelativeTMASegment.CUT_OFF_VALUE_DEGS);
        if (prefValue != null && prefValue.length() > 0 && Double.valueOf(
            prefValue) != null)
        {
          cutOffValue = Double.valueOf(prefValue);
        }
        else
        {
          cutOffValue = 3d;
        }

        final Paint errorColor = calculateErrorShadeFor(dotPlotData,
            cutOffValue);
        // dotPlot.setBackgroundPaint(errorColor);
        backShader.setShade(errorColor);
      }

      // linePlot.setDataset(actualSeries);
      // targetPlot.setDataset(0, targetCourseSeries);
      // targetPlot.setDataset(1, targetSpeedSeries);
    }
    finally
    {
      // now switch off updates
      for (final Series series : sList)
      {
        series.setNotify(true);
      }
      // now switch off updates
      for (final TimeSeriesCollection series : tList)
      {
        series.setNotify(true);
      }
    }
  }

  /**
   * go through the tracks, finding the relevant position on the other track.
   *
   */
  private void updateDoublets(final boolean onlyVis, final boolean needBearing,
      final boolean needFreq)
  {
    // ok - we're now there
    // so, do we have primary and secondary tracks?
    if (_primaryTrack != null)
    {
      // cool sort out the list of sensor locations for these tracks
      _primaryDoublets = getDoublets(_primaryTrack, _secondaryTrack, onlyVis,
          needBearing, needFreq);
    }
  }

  private static Color halfWayColor(final Color a, final Color b)
  {
    int red = (a.getRed() + b.getRed()) / 2;
    int blue = (a.getBlue() + b.getBlue()) / 2;
    int green = (a.getGreen() + b.getGreen()) / 2;
    return new Color(red, blue, green);
  }

  /**
   * ok, our track has been dragged, calculate the new series of offsets
   *
   * @param linePlot
   * @param dotPlot
   * @param onlyVis
   * @param holder
   * @param logger
   * @param fZeroMarker
   *
   * @param currentOffset
   *          how far the current track has been dragged
   */
  public void updateFrequencyData(final TimeSeriesCollection dotPlotData,
      final TimeSeriesCollection linePlotData, final TrackDataProvider tracks,
      final boolean onlyVis, final ErrorLogger logger,
      final boolean updateDoublets, final SetBackgroundShade backShader,
      final ColourStandardXYItemRenderer lineRend)
  {

    // do we have anything?
    if (_primaryTrack == null)
    {
      return;
    }

    // ok, find the track wrappers
    if (_secondaryTrack == null)
    {
      initialise(tracks, false, onlyVis, logger, "Frequency", false, true);
    }

    // ok - the tracks have moved. better update the doublets
    if (updateDoublets)
    {
      updateDoublets(onlyVis, false, true);
    }

    // aah - but what if we've ditched our doublets?
    if ((_primaryDoublets == null) || (_primaryDoublets.size() == 0))
    {
      // better clear the plot
      dotPlotData.removeAllSeries();
      linePlotData.removeAllSeries();
      return;
    }

    // create the collection of series
    // final TimeSeriesCollection errorSeries = new TimeSeriesCollection();
    // final TimeSeriesCollection actualSeries = new TimeSeriesCollection();
    final TimeSeriesCollection baseValuesSeries = new TimeSeriesCollection();

    if (_primaryTrack == null)
    {
      return;
    }

    final TimeSeriesCollection measuredValuesColl = new TimeSeriesCollection();

    // final TimeSeries correctedValues = new TimeSeries("Corrected");
    final TimeSeriesCollection predictedValuesColl = new TimeSeriesCollection();

    // ok, run through the points on the primary track
    final Iterator<Doublet> iter = _primaryDoublets.iterator();
    SensorWrapper lastSensor = null;

    // sort out the speed of sound
    final String speedStr = CorePlugin.getDefault().getPreferenceStore()
        .getString(FrequencyCalcs.SPEED_OF_SOUND_KTS_PROPERTY);
    final double speedOfSound;
    if (speedStr != null && speedStr.length() > 0)
    {
      speedOfSound = Double.parseDouble(speedStr);
    }
    else
    {
      speedOfSound = FrequencyCalcs.SpeedOfSoundKts;
    }

    while (iter.hasNext())
    {
      final Doublet thisD = iter.next();
      try
      {

        final Color thisColor = thisD.getColor();
        final double measuredFreq = thisD.getMeasuredFrequency();
        final HiResDate currentTime = thisD.getDTG();
        final FixedMillisecond thisMilli = new FixedMillisecond(currentTime
            .getDate().getTime());

        final ColouredDataItem mFreq = new ColouredDataItem(thisMilli,
            measuredFreq, thisColor, false, null, true, true, thisD
                .getSensorCut());

        // final ColouredDataItem corrFreq = new ColouredDataItem(
        // new FixedMillisecond(currentTime.getDate().getTime()),
        // correctedFreq, thisColor, false, null);
        final SensorWrapper thisSensor = thisD.getSensorCut().getSensor();
        final String sensorName = thisSensor.getName();
        TimeSeries measuredValues = measuredValuesColl.getSeries(sensorName);
        if (measuredValues == null)
        {
          measuredValues = new TimeSeries(sensorName);
          measuredValuesColl.addSeries(measuredValues);
        }
        measuredValues.add(mFreq);

        final double baseFreq = thisD.getBaseFrequency();
        if (!Double.isNaN(baseFreq))
        {
          // have we changed sensor?
          final boolean newSensor;
          if (thisSensor != null && !thisSensor.equals(lastSensor))
          {
            newSensor = true;
            lastSensor = thisSensor;
          }
          else
          {
            newSensor = false;
          }

          final ColouredDataItem bFreq = new ColouredDataItem(thisMilli,
              baseFreq, thisColor.darker(), !newSensor, null, true, true);
          TimeSeries baseValues = baseValuesSeries.getSeries(sensorName
              + "(base)");
          if (baseValues == null)
          {
            baseValues = new TimeSeries(sensorName + "(base)");
            baseValuesSeries.addSeries(baseValues);
          }
          baseValues.add(bFreq);

          // do we have target data?
          if (thisD.getTarget() != null)
          {
            final Color calcColor = thisD.getTarget().getColor();

            // did we get a base frequency? We may have a track
            // with a section of data that doesn't have frequency, you see.
            final double predictedFreq = thisD.getPredictedFrequency(
                speedOfSound);
            final double thisError = thisD.calculateFreqError(measuredFreq,
                predictedFreq);
            final Color predictedColor = halfWayColor(calcColor, thisColor);
            final ColouredDataItem pFreq = new ColouredDataItem(thisMilli,
                predictedFreq, predictedColor, true, null, true, true, thisD
                    .getTarget());

            final ColouredDataItem eFreq = new ColouredDataItem(thisMilli,
                thisError, thisColor, false, null, true, true);
            TimeSeries predictedValues = predictedValuesColl.getSeries(
                sensorName);
            if (predictedValues == null)
            {
              predictedValues = new TimeSeries(sensorName);
              predictedValuesColl.addSeries(predictedValues);
            }
            predictedValues.addOrUpdate(pFreq);

            TimeSeries errorValues = dotPlotData.getSeries(thisSensor
                .getName());
            if (errorValues == null)
            {
              errorValues = new TimeSeries(thisSensor.getName());
              dotPlotData.addSeries(errorValues);
            }
            errorValues.add(eFreq);
          } // if we have a target
        } // if we have a base frequency
      }
      catch (final SeriesException e)
      {
        CorePlugin.logError(IStatus.INFO,
            "some kind of trip whilst updating frequency plot", e);
      }

    }

    // find the color for maximum value in the error series, if we have error data
    if (dotPlotData.getSeriesCount() > 0)
    {
      final double cutOffValue;

      // retrieve the cut-off value
      final String prefValue = Application.getThisProperty(
          RelativeTMASegment.CUT_OFF_VALUE_HZ);
      if (prefValue != null && prefValue.length() > 0 && Double.valueOf(
          prefValue) != null)
      {
        cutOffValue = Double.valueOf(prefValue) / 100d;
      }
      else
      {
        cutOffValue = 1d;
      }

      final Paint errorColor = calculateErrorShadeFor(dotPlotData, cutOffValue);
      backShader.setShade(errorColor);
    }

    Iterator<?> mIter = measuredValuesColl.getSeries().iterator();
    while (mIter.hasNext())
    {
      TimeSeries series = (TimeSeries) mIter.next();
      linePlotData.addSeries(series);
    }

    // actualSeries.addSeries(correctedValues);
    Iterator<?> pIter = predictedValuesColl.getSeries().iterator();
    while (pIter.hasNext())
    {
      TimeSeries predictedValues = (TimeSeries) pIter.next();
      linePlotData.addSeries(predictedValues);
    }

    if (baseValuesSeries.getSeries().size() > 0)
    {
      Iterator<?> bIter = baseValuesSeries.getSeries().iterator();
      while (bIter.hasNext())
      {
        TimeSeries baseValues = (TimeSeries) bIter.next();
        linePlotData.addSeries(baseValues);
      }
      // sort out the rendering for the BaseFrequencies.
      // we want to show a solid line, with no markers
      final int BaseFreqSeries = 2;
      lineRend.setSeriesShape(BaseFreqSeries, ShapeUtilities.createDiamond(
          0.2f));
      lineRend.setSeriesStroke(BaseFreqSeries, new BasicStroke(4));
      lineRend.setSeriesShapesVisible(BaseFreqSeries, false);
      lineRend.setSeriesShapesFilled(BaseFreqSeries, false);
    }

  }
}
