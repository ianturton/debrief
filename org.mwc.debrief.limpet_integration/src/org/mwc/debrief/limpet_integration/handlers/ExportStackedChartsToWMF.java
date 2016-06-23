package org.mwc.debrief.limpet_integration.handlers;

import info.limpet.stackedcharts.ui.view.StackedChartsView;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.commands.IHandlerListener;
import org.eclipse.core.expressions.EvaluationContext;
import org.eclipse.core.expressions.IEvaluationContext;
import org.eclipse.swt.graphics.GC;
import org.jfree.experimental.chart.swt.ChartComposite;

public class ExportStackedChartsToWMF implements IHandler
{

  @Override
  public void addHandlerListener(IHandlerListener handlerListener)
  {
    // TODO Auto-generated method stub

  }

  @Override
  public void dispose()
  {
    // TODO Auto-generated method stub

  }

  
  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException
  {
    System.out.println("DOING EXPORT TO WMF");

    StackedChartsView view = getChartView(event);
    
    if(view != null)
    {
      final ChartComposite composite = view.getChartComposite();
      
      // create the WMF graphics      
      GC wmf = null;
      
      // paint to the graphics
      composite.print(wmf );
      
      // put the WMF on the clipboard
    }
    
    return null;
  }


  private StackedChartsView getChartView(ExecutionEvent event)
  {
    StackedChartsView view = null;
    // try to find the view that clicked on us
    Object context = event.getApplicationContext();
    if (context instanceof EvaluationContext)
    {
      EvaluationContext eC = (EvaluationContext) context;
      IEvaluationContext parent = eC.getParent();
      Object part = parent.getVariable("activePart");
      if(part != null)
      {
        if(part instanceof StackedChartsView)
        {
          view = (StackedChartsView) part;
        }
      }
    }
    return view;
  }

  @Override
  public boolean isEnabled()
  {
    // TODO Auto-generated method stub
    return true;
  }

  @Override
  public boolean isHandled()
  {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public void removeHandlerListener(IHandlerListener handlerListener)
  {
    // TODO Auto-generated method stub

  }

}
