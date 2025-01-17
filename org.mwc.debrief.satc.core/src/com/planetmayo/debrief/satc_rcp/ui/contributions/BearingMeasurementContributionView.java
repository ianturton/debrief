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
package com.planetmayo.debrief.satc_rcp.ui.contributions;

import java.util.List;

import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.beans.BeansObservables;
import org.eclipse.core.databinding.observable.value.IObservableValue;
import org.eclipse.nebula.widgets.cdatetime.CDT;
import org.eclipse.nebula.widgets.cdatetime.CDateTime;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.swt.widgets.Text;

import com.planetmayo.debrief.satc.model.contributions.BearingMeasurementContribution;
import com.planetmayo.debrief.satc.model.contributions.CoreMeasurementContribution;
import com.planetmayo.debrief.satc.model.generator.IContributions;
import com.planetmayo.debrief.satc.zigdetector.LegOfData;
import com.planetmayo.debrief.satc_rcp.SATC_Activator;
import com.planetmayo.debrief.satc_rcp.ui.UIUtils;
import com.planetmayo.debrief.satc_rcp.ui.converters.BooleanToNullConverter;
import com.planetmayo.debrief.satc_rcp.ui.converters.PrefixSuffixLabelConverter;
import com.planetmayo.debrief.satc_rcp.ui.converters.units.UnitConverter;

@SuppressWarnings("deprecation")
public class BearingMeasurementContributionView extends
    BaseContributionView<BearingMeasurementContribution>
{
  private Scale errorSlider;
  private Label errorLabel;
  private Button errorActiveCheckbox;
  private Button runSliceOsBtn;
  private Button runSliceTgtBtn;

  public BearingMeasurementContributionView(final Composite parent,
      final BearingMeasurementContribution contribution,
      final IContributions contributions)
  {
    super(parent, contribution, contributions);
    initUI();
  }

  @Override
  protected void bindValues(final DataBindingContext context)
  {
    final PrefixSuffixLabelConverter labelConverter =
        new PrefixSuffixLabelConverter(Object.class, "+/- ", " degs");
    labelConverter.setNestedUnitConverter(UnitConverter.ANGLE_DEG
        .getModelToUI());
    final IObservableValue errorValue = BeansObservables.observeValue(
        contribution, BearingMeasurementContribution.BEARING_ERROR);
    final IObservableValue observationNumberValue = BeansObservables
        .observeValue(contribution,
            CoreMeasurementContribution.OBSERVATIONS_NUMBER);
    bindCommonHeaderWidgets(context, errorValue, observationNumberValue,
        new PrefixSuffixLabelConverter(Object.class, " Measurements"),
        labelConverter);
    bindCommonDates(context);

    bindSliderLabelCheckbox(context, errorValue, errorSlider, errorLabel,
        errorActiveCheckbox, labelConverter, new BooleanToNullConverter<Double>(
            0d), UnitConverter.ANGLE_DEG);

    // connect up the MDA toggle (note - we've switched from a toggle to a button
    // IObservableValue autoValue = BeansObservables.observeValue(contribution,
    // BearingMeasurementContribution.RUN_MDA);
    // IObservableValue autoButton = WidgetProperties.selection().observe(
    // runMDACheckbox);
    // context.bindValue(autoButton, autoValue);

    // connect the checkbox to the run MDA event
    runSliceOsBtn.addSelectionListener(new SelectionListener()
    {
      @Override
      public void widgetDefaultSelected(final SelectionEvent e)
      {
      }

      @Override
      public void widgetSelected(final SelectionEvent e)
      {

        // ok - run the MDA generator
        contribution.sliceOwnship(getContributions());

        // ok, done - enable the second btn
        runSliceTgtBtn.setEnabled(true);

        // share the good news
        final List<LegOfData> oLegs = contribution.getOwnshipLegs();
        if (oLegs != null && !oLegs.isEmpty())
        {
          final int num = oLegs.size();
          final String message = "Ownship sliced into " + num + " legs.";
          SATC_Activator.showMessage("Slice ownship legs", message);
        }
      }
    });
    // connect the checkbox to the run MDA event
    runSliceTgtBtn.addSelectionListener(new SelectionListener()
    {
      @Override
      public void widgetDefaultSelected(final SelectionEvent e)
      {
      }

      @Override
      public void widgetSelected(final SelectionEvent e)
      {
        // ok - run the MDA generator
        contribution.runMDA(getContributions());
      }
    });

  }

  @Override
  protected void createBody(final Composite parent)
  {
    final GridData layoutData = new GridData();
    layoutData.horizontalIndent = 15;
    layoutData.exclude = true;
    layoutData.grabExcessVerticalSpace = true;
    layoutData.grabExcessHorizontalSpace = true;
    layoutData.horizontalAlignment = SWT.FILL;
    layoutData.verticalAlignment = SWT.FILL;

    bodyGroup = new Group(parent, SWT.SHADOW_ETCHED_IN);
    bodyGroup.setLayoutData(layoutData);
    bodyGroup.setText("Adjust");
    bodyGroup.setLayout(new GridLayout(4, false));

    UIUtils.createLabel(bodyGroup, "Name:", new GridData(70, SWT.DEFAULT));
    contributionNameText = new Text(bodyGroup, SWT.BORDER);
    GridData gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.horizontalSpan = 3;
    contributionNameText.setLayoutData(gd);

    UIUtils.createLabel(bodyGroup, "Dates:", new GridData());
    gd = new GridData();
    gd.horizontalSpan = 3;
    final Composite datesGroup = UIUtils.createEmptyComposite(bodyGroup,
        new RowLayout(SWT.HORIZONTAL), gd);
    startDate = new CDateTime(datesGroup, CDT.BORDER | CDT.DROP_DOWN
        | CDT.DATE_SHORT);
    startDate.setPattern("dd/MM/yyyy");
    startTime = new CDateTime(datesGroup, CDT.BORDER | CDT.SPINNER
        | CDT.TIME_MEDIUM);
    UIUtils.createLabel(datesGroup, "  -  ", new RowData());
    endDate = new CDateTime(datesGroup, CDT.BORDER | CDT.DROP_DOWN
        | CDT.DATE_SHORT);
    endDate.setPattern("dd/MM/yyyy");
    endTime = new CDateTime(datesGroup, CDT.BORDER | CDT.SPINNER
        | CDT.TIME_MEDIUM);

    createLimitAndEstimateSliders();
  }

  @Override
  protected void createLimitAndEstimateSliders()
  {
    UIUtils.createLabel(bodyGroup, "Error: ", new GridData(
        GridData.HORIZONTAL_ALIGN_FILL));

    final Composite group = new Composite(bodyGroup, SWT.NONE);
    group.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));
    group.setLayout(UIUtils.createGridLayoutWithoutMargins(2, false));
    errorActiveCheckbox = new Button(group, SWT.CHECK);
    errorLabel = UIUtils.createSpacer(group, new GridData(
        GridData.FILL_HORIZONTAL));

    errorSlider = new Scale(bodyGroup, SWT.HORIZONTAL);
    final GridData gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.horizontalSpan = 2;
    errorSlider.setLayoutData(gd);

    // and now the MDA components
    UIUtils.createLabel(bodyGroup, "MDA: ", new GridData(
        GridData.HORIZONTAL_ALIGN_FILL));
    // Composite group2 = new Composite(bodyGroup, SWT.NONE);
    // group2.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));
    // group2.setLayout(UIUtils.createGridLayoutWithoutMargins(5, false));
    runSliceOsBtn = new Button(bodyGroup, SWT.PUSH);
    runSliceOsBtn.setText("1. Slice O/S legs");
    runSliceTgtBtn = new Button(bodyGroup, SWT.PUSH);
    runSliceTgtBtn.setText("2. Slice Tgt legs");
    UIUtils.createLabel(bodyGroup, "Auto-detect target manoeuvres",
        new GridData(GridData.HORIZONTAL_ALIGN_FILL));
  }

  @Override
  protected String getTitlePrefix()
  {
    return "Bearing Measurement - ";
  }

  @Override
  protected void initializeWidgets()
  {
    startDate.setEnabled(false);
    startTime.setEnabled(false);
    endDate.setEnabled(false);
    endTime.setEnabled(false);

    runSliceOsBtn.setEnabled(true);
    runSliceTgtBtn.setEnabled(false);
  }
}
