/**
 * 
 */
package org.mwc.cmap.plot3d.actions;

import org.eclipse.core.runtime.Status;
import org.eclipse.ui.*;
import org.mwc.cmap.core.CorePlugin;
import org.mwc.cmap.core.DataTypes.Temporal.TimeProvider;
import org.mwc.cmap.plot3d.views.Plot3dView;
import org.mwc.cmap.plotViewer.actions.CoreEditorAction;
import org.mwc.cmap.plotViewer.editors.CorePlotEditor;

import MWC.GUI.*;

/**
 * @author ian.mayo
 */
public class View3d extends CoreEditorAction
{
	public static ToolParent _theParent = null;

	/**
	 * ok, store who the parent is for the operation
	 * 
	 * @param theParent
	 */
	public static void init(ToolParent theParent)
	{
		_theParent = theParent;
	}

	/**
	 * and execute..
	 */
	protected void execute()
	{
		CorePlugin.logError(Status.INFO, "Starting to open 3d view", null);
		
		try{
		final PlainChart theChart = getChart();
		Layers theLayers = theChart.getLayers();		
//		View3dPlot plotter = new View3dPlot(_theParent, null, theLayers, null);		
//		plotter.execute();
		
		CorePlugin.logError(Status.INFO, "Found source data", null);
	

			IWorkbench wb = PlatformUI.getWorkbench();
			IWorkbenchWindow win = wb.getActiveWorkbenchWindow();
			IWorkbenchPage page = win.getActivePage();

			CorePlugin.logError(Status.INFO, "Found page", null);
			
			// ok, get the editor = we want some time control
			CorePlotEditor cpe = (CorePlotEditor) page.getActiveEditor();
			TimeProvider timer = (TimeProvider) cpe.getAdapter(TimeProvider.class);
			
			CorePlugin.logError(Status.INFO, "Found plot editor & time provider", null);

			try
			{
				
				// ////////////////////////////////////////////////
				// sort out the title
				// ////////////////////////////////////////////////
				// get the title to use
				String theTitle = "3D - " + cpe.getTitle();

				// and the plot itself
				String plotId = "org.mwc.cmap.plot3d.views.Plot3dView";
				page.showView(plotId, theTitle, IWorkbenchPage.VIEW_ACTIVATE);

				CorePlugin.logError(Status.INFO, "Show view called", null);
				

				//				// put our subjects into a vector
//				Vector theTracks = new Vector(0, 1);
//				for (int i = 0; i < subjects.length; i++)
//				{
//					Editable thisS = subjects[i];
//					theTracks.add(thisS);
//				}
//
//				// right, now for the data
//				AbstractDataset ds = ShowTimeVariablePlot2.getDataSeries(thePrimary, theHolder,
//						theTracks, startTime, endTime, null);

				// ok, try to retrieve the view
				IViewReference plotRef = page.findViewReference(plotId, theTitle);
				Plot3dView ourPlot = (Plot3dView) plotRef.getView(true);

				CorePlugin.logError(Status.INFO, "Found plot, about to call show plot", null);
				
				ourPlot.showPlot(theTitle, theLayers, timer);
				
				CorePlugin.logError(Status.INFO, "Show plot called", null);
			}
			catch (PartInitException e)
			{
				e.printStackTrace();
			}

		


//	// ok - set the image descriptor
//	viewPlot.setImageDescriptor(Plot3dPlugin
//			.getImageDescriptor("icons/document_chart.png"));		
//		
	
	
	
	
		//	World.main(new String[]{});
		}
		catch(NoClassDefFoundError err)
		{
			CorePlugin.showMessage("View 3d", "Debrief NGs 3d implementation invalid.  This is a known problem");
			CorePlugin.logError(Status.ERROR, "3d libraries not found", err);
		}
		
	}

}