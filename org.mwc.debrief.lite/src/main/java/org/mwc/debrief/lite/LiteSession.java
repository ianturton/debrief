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
package org.mwc.debrief.lite;

import java.awt.datatransfer.Clipboard;

import Debrief.GUI.Frames.Session;
import Debrief.GUI.Tote.StepControl;
import MWC.GUI.Layers;
import MWC.GUI.ToolParent;

public class LiteSession extends Session
{
  /**
   *
   */
  private static final long serialVersionUID = 1L;
  private StepControl _stepper;

  public LiteSession(final Clipboard clipboard, final Layers layers)
  {
    super(clipboard, layers);
  }

  @Override
  public void closeGUI()
  {
    // Nothing to do here.
  }

  @Override
  public StepControl getStepControl()
  {
    return _stepper;
  }

  @Override
  public void initialiseForm(final ToolParent theParent)
  {
    throw new IllegalArgumentException("Not implemented");
  }

  @Override
  public void repaint()
  {
    throw new IllegalArgumentException("Not implemented");
  }

  public void setStepper(final StepControl _stepper)
  {
    this._stepper = _stepper;
  }

  @Override
  protected boolean wantsToClose()
  {
    // action handled by DebriefLiteApp class.
    return true;
  }

}