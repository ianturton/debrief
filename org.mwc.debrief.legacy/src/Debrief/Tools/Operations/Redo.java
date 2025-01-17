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
package Debrief.Tools.Operations;

import Debrief.GUI.Frames.*;
import MWC.GUI.Tools.*;
import MWC.GUI.Undo.UndoBuffer;

public final class Redo extends PlainTool
{
  /////////////////////////////////////////////////////////////
  // member variables
  ////////////////////////////////////////////////////////////
  private final Application _theParent;

  /////////////////////////////////////////////////////////////
  // constructor
  ////////////////////////////////////////////////////////////
  public Redo(final Application theParent){
    super(theParent, "Redo", "images/24/redo.png");
    
    _theParent = theParent;
  }
  
  /////////////////////////////////////////////////////////////
  // member functions
  ////////////////////////////////////////////////////////////
  public final Action getData()
  {
    final Session theSession = _theParent.getCurrentSession();
    
    if(theSession != null)
      return new RedoAction(theSession.getUndoBuffer());
    else
      return null;
  }


  ///////////////////////////////////////////////////////
  // store action information
  ///////////////////////////////////////////////////////
  final static class RedoAction implements Action{
    /** the buffer we are 'doing'
     */
    final UndoBuffer _theBuffer;
    
    public RedoAction(final UndoBuffer theBuffer){
      _theBuffer = theBuffer;
    }
    
    public final boolean isUndoable(){
      return false;
    }

    public final boolean isRedoable(){
      return false;
    }
    
    public final String toString(){
      return null;
    }                                        
    
    public final void undo(){
      // scrap item
    }

    public final void execute(){
      _theBuffer.redo();
    }

  }

}
