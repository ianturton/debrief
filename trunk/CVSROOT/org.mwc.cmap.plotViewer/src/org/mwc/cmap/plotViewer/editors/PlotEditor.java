package org.mwc.cmap.plotViewer.editors;

import interfaces.IControllableView;
import interfaces.IResourceProvider;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Enumeration;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.DropTargetListener;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.ide.IGotoMarker;
import org.eclipse.ui.part.EditorPart;
import org.mwc.cmap.core.DataTypes.Narrative.NarrativeProvider;
import org.mwc.cmap.core.DataTypes.Temporal.ControllableTime;
import org.mwc.cmap.core.DataTypes.Temporal.TimeManager;
import org.mwc.cmap.core.DataTypes.Temporal.TimeProvider;

import Debrief.Wrappers.NarrativeWrapper;
import MWC.Algorithms.PlainProjection;
import MWC.GUI.Layer;
import MWC.GUI.Layers;
import MWC.GenericData.HiResDate;
import MWC.GenericData.WorldArea;
import MWC.Utilities.TextFormatting.DebriefFormatDateTime;

public abstract class PlotEditor extends EditorPart implements IResourceProvider,
	IControllableView
{

	////////////////////////////////
	// member data
	////////////////////////////////
	
	/** the graphic data we know about
	 * 
	 */
	protected Layers _myLayers;
	
	/** handle narrative management
	 * 
	 */
	protected NarrativeProvider _theNarrativeProvider;
	
	/** an object to look after all of the time bits
	 *
	 */
	protected TimeManager _timeManager;
	
	/** the object which listens to time-change events.  we remember
	 * it so that it can be deleted when we close
	 */
	protected PropertyChangeListener _timeListener;

	
	// drag-drop bits
	
	protected DropTarget target;
	
	/////////////////////////////////////////////////
	// dummy bits applicable for our dummy interface
	/////////////////////////////////////////////////
	Button _myButton;
	Label _myLabel;

	private Composite _plotPanel;	
	
	////////////////////////////////
	// constructor
	////////////////////////////////
	
	public PlotEditor() {
		super();
		
		// create the time manager.  cool
		_timeManager = new TimeManager();
		
		// and listen for new times
		_timeListener = new PropertyChangeListener()
		{
			public void propertyChange(PropertyChangeEvent arg0)
			{
				// right, the time has changed.  better redraw parts of the plot
				updateLabel();
			}
		};
		
		_timeManager.addListener(_timeListener, TimeProvider.TIME_CHANGED_PROPERTY_NAME);

	}

	public void dispose() {
		super.dispose();
		
		// stop listening to the time manager
		_timeManager.removeListener(_timeListener, TimeProvider.TIME_CHANGED_PROPERTY_NAME);
	}
	public void doSave(IProgressMonitor monitor) {
		// TODO Auto-generated method stub
		
	}
	public void doSaveAs() {
		// TODO Auto-generated method stub
		
	}

	public boolean isDirty() {
		// TODO Auto-generated method stub
		return false;
	}
	public boolean isSaveAsAllowed() {
		// TODO Auto-generated method stub
		return false;
	}

	
	public void createPartControl(Composite parent) {
		_plotPanel = new Composite(parent, SWT.NONE);
		_plotPanel.setLayout(new FillLayout());
		_myButton = new Button(_plotPanel, SWT.NONE);
		_myButton.setText("push me");
		

		
		_myButton.addSelectionListener(new SelectionListener(){

			public void widgetSelected(SelectionEvent e) {
				updateLabel();
			}

			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});
		
		_myLabel = new Label(_plotPanel, SWT.NONE);
		_myLabel.setText("the label");

		//and the drop support
		configureFileDropSupport();
		
	}
	
	/** sort out the file-drop target
	 * 
	 *
	 */
	private void configureFileDropSupport()
	{
		int dropOperation = DND.DROP_COPY;
		Transfer[] dropTypes = {FileTransfer.getInstance()};
			
		target = new DropTarget(_plotPanel, dropOperation);
		target.setTransfer(dropTypes);
		target.addDropListener(new DropTargetListener()
		{
			public void dragEnter(DropTargetEvent event)
			{
				if(FileTransfer.getInstance().isSupportedType(event.currentDataType))
				{
					if(event.detail != DND.DROP_COPY)
					{
						event.detail = DND.DROP_COPY;
					}
				}
			}

			public void dragLeave(DropTargetEvent event)
			{			}

			public void dragOperationChanged(DropTargetEvent event)
			{			}

			public void dragOver(DropTargetEvent event)
			{			}

			public void dropAccept(DropTargetEvent event)
			{			}

			public void drop(DropTargetEvent event)
			{
				String[] fileNames = null;
				if (FileTransfer.getInstance().isSupportedType(event.currentDataType))
				{
					fileNames = (String[])event.data;			
				}				
				if(fileNames != null)
				{
					filesDropped(fileNames);
				}
			}
			
		});
		
		
	}

	/** process the files dropped onto this panel
	 * 
	 * @param fileNames list of filenames
	 */
	protected void filesDropped(String[] fileNames)
	{
		System.out.println("Files dropped");
	}

	public void setFocus() {
		// TODO Auto-generated method stub
		
	}

	public Object getAdapter(Class adapter)
	{
		Object res = null;
		
		// so, is he looking for the layers?
		if(adapter == Layers.class)
		{
			if(_myLayers != null)
				res = _myLayers;
		}
		else if(adapter == NarrativeProvider.class)
		{
			return _theNarrativeProvider;
		}
		else if(adapter == TimeProvider.class)
		{
			return _timeManager;
		}
		else if(adapter == ControllableTime.class)
		{
			return _timeManager;
		}
		else if (adapter == IGotoMarker.class)
		{
			return new IGotoMarker()
			{
				public void gotoMarker(IMarker marker)
				{
						String lineNum = marker.getAttribute(IMarker.LINE_NUMBER, "na");
						if(lineNum != "na")
						{
							// right, convert to DTG
							HiResDate tNow = new HiResDate(0, Long.parseLong(lineNum));
						  _timeManager.setTime(this, tNow);
						}
				}
				
			};
		}
		
		return res;
	}

	private static String describeData(String dataName, Layers theLayers, 
				NarrativeWrapper narrative, TimeManager timeManager)
	{
		String res = dataName + "\n";
		
		Enumeration enumer = theLayers.elements();
		while(enumer.hasMoreElements())
		{
			Layer thisL = (Layer) enumer.nextElement();
			res = res + thisL.getName() + "\n"; 
		}
		
		if(narrative != null)
		{
			res = res + "Narrative:" + narrative.getData().size() + " elements" + "\n";
		}
		else
		{
			res = res + "Narrative empty\n";
		}
		
		if(timeManager != null)
		{
			HiResDate tNow = timeManager.getTime();
			if(tNow != null)			
				res = res + DebriefFormatDateTime.toStringHiRes(tNow);
			else
				res = res + " time not set";
		}
		
		return res;
	}
	
	/** ok, the time has changed.  update our own time, inform the listeners
	 * 
	 * @param origin
	 * @param newDate
	 */
	public void setNewTime(Object origin, HiResDate newDate)
		{
				updateLabel();
		}
	
	private void updateLabel()
	{
		String msg = "No data yet";
		if(_theNarrativeProvider != null)
			msg = describeData(getEditorInput().getName(),
				_myLayers, _theNarrativeProvider.getNarrative(), _timeManager);
		else
			msg = describeData(getEditorInput().getName(),
					_myLayers, null, _timeManager);
		
		
		if(_myLabel != null)
			_myLabel.setText(msg);
	}
	
	/** method called when a helper object has completed a plot-load operation
	 * 
	 * @param source
	 */
	abstract public void loadingComplete(Object source);
	
	/** return the file representing where this plot is stored
	 * 
	 * @return the file location
	 */
	public IResource getResource()
	{
		// have we been saved yet?
		return null;
	}

	public WorldArea getViewport()
	{
		// TODO Auto-generated method stub
		return null;
	}

	public void setViewport(WorldArea target)
	{
		// TODO Auto-generated method stub
		
	}

	public PlainProjection getProjection()
	{
		// TODO Auto-generated method stub
		return null;
	}

	public void setProjection(PlainProjection proj)
	{
		// TODO Auto-generated method stub
		
	}
	
	
	
}
