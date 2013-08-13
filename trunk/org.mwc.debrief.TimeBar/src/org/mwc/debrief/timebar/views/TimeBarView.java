package org.mwc.debrief.timebar.views;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.part.ViewPart;
import org.mwc.cmap.TimeController.views.TimeController;
import org.mwc.cmap.core.CorePlugin;
import org.mwc.cmap.core.DataTypes.Temporal.TimeProvider;
import org.mwc.cmap.core.property_support.EditableWrapper;
import org.mwc.cmap.core.ui_support.PartMonitor;
import org.mwc.debrief.timebar.Activator;

import MWC.GUI.Editable;
import MWC.GUI.Layer;
import MWC.GUI.Layers;
import MWC.GUI.Plottable;
import MWC.GenericData.HiResDate;


public class TimeBarView extends ViewPart {
	
	TimeBarViewer _viewer;
	/**
	 * helper application to help track creation/activation of new plots
	 */
	private PartMonitor _myPartMonitor;
	
	/**
	 * Debrief data
	 */
	private Layers _myLayers;
	
	TimeProvider _timeProvider;
	/**
	 * listen out for new times
	 */
	private PropertyChangeListener _temporalListener = new NewTimeListener();

	private Layers.DataListener _myLayersListener;

	private ISelectionChangedListener _selectionChangeListener;
	
	
	/**
	 * Actions to zoom around the time bars
	 */
	private Action _zoomInAction;
	private Action _zoomOutAction;	
	private Action _fitToWindowAction;
	
	
	@Override
	public void createPartControl(Composite parent) {
		_viewer = new TimeBarViewer(parent, _myLayers);

		getSite().setSelectionProvider(_viewer);

		_myPartMonitor = new PartMonitor(getSite().getWorkbenchWindow()
				.getPartService());

		listenToMyParts();
		makeActions();
		contributeToActionBars();

		_selectionChangeListener = new ISelectionChangedListener() {

			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				ISelection sel = event.getSelection();
				if (!(sel instanceof IStructuredSelection))
					return;
				IStructuredSelection ss = (IStructuredSelection) sel;
				Object o = ss.getFirstElement();
				if (o instanceof EditableWrapper) {
					EditableWrapper pw = (EditableWrapper) o;
					editableSelected(sel, pw);
				}
			}
		};
		_viewer.addSelectionChangedListener(_selectionChangeListener);
	}
	
	private void contributeToActionBars()
	{
		IActionBars bars = getViewSite().getActionBars();
		fillLocalToolBar(bars.getToolBarManager());
	}
	
	private void fillLocalToolBar(IToolBarManager manager)
	{
		manager.add(_zoomInAction);
		manager.add(_zoomOutAction);
		manager.add(_fitToWindowAction);
	}
	
	private void makeActions()
	{
		_zoomInAction = new Action("Zoom in", Action.AS_PUSH_BUTTON)
		{
			public void run()
			{
				_viewer.zoomIn();
			}
		};
		_zoomInAction.setText("Zoom in");
		_zoomInAction
				.setToolTipText("Zoom in");
		_zoomInAction.setImageDescriptor(Activator
				.getImageDescriptor("icons/zoomin.gif"));
		
		_zoomOutAction = new Action("Zoom out", Action.AS_PUSH_BUTTON)
		{
			public void run()
			{
				_viewer.zoomOut();
			}		
		};
		_zoomOutAction.setText("Zoom out");
		_zoomOutAction
				.setToolTipText("Zoom out");
		_zoomOutAction.setImageDescriptor(Activator
				.getImageDescriptor("icons/zoomout.gif"));
		
		_fitToWindowAction = new Action("Fit to Window", Action.AS_PUSH_BUTTON)
		{
			public void run()
			{
				_viewer.fitToWindow();
			}
		};
		_fitToWindowAction.setText("Fit to Window");
		_fitToWindowAction
				.setToolTipText("Fit to Window");
		_fitToWindowAction.setImageDescriptor(CorePlugin
				.getImageDescriptor("icons/fit_to_win.gif"));
		
	}
	
	void processNewLayers(Object part)
	{
		// just check we're not already looking at it
		if (part.equals(_myLayers))
			return;
		
		_myLayers = (Layers) part;
		if (_myLayersListener == null)
		{
			_myLayersListener = new Layers.DataListener2()
			{

				public void dataModified(Layers theData, Layer changedLayer)
				{						
				}

				public void dataExtended(Layers theData)
				{
					dataExtended(theData, null, null);
				}

				public void dataReformatted(Layers theData, Layer changedLayer)
				{
					processReformattedLayer(theData, changedLayer);
				}

				public void dataExtended(Layers theData, Plottable newItem,
								Layer parentLayer)
				{
					processNewData(theData, newItem, parentLayer);
				}
			};
		}
		// right, listen for data being added
		_myLayers.addDataExtendedListener(_myLayersListener);

		// and listen for items being reformatted
		_myLayers.addDataReformattedListener(_myLayersListener);

		// do an initial population.
		processNewData(_myLayers, null, null);		
	}	
	
	void processReformattedLayer(Layers theData, Layer changedLayer)
	{
		_viewer.drawDiagram(theData);
	}
		
	void processNewData(final Layers theData, final Editable newItem,
			final Layer parentLayer)
	{
			Display.getDefault().asyncExec(new Runnable()
			{
				public void run()
				{
					// ok, fire the change in the UI thread
					_viewer.drawDiagram(theData, true /* jump to begin */);
					// hmm, do we know about the new item? If so, better select it
					if (newItem != null)
					{
						// wrap the plottable
						EditableWrapper parentWrapper = new EditableWrapper(parentLayer,
								null, theData);
						EditableWrapper wrapped = new EditableWrapper(newItem,
								parentWrapper, theData);
						ISelection selected = new StructuredSelection(wrapped);

						// and select it
						editableSelected(selected, wrapped);
					}
				}
			});
	}	
	
	@Override
	public void setFocus() 
	{
		_viewer.setFocus();		
	}
	
	public void dispose()
	{
		super.dispose();

		// make sure we close the listeners
		clearLayerListener();
		if (_timeProvider != null)
		{
			_timeProvider.removeListener(_temporalListener, 
					TimeProvider.TIME_CHANGED_PROPERTY_NAME);
			_temporalListener = null;
			_timeProvider = null;
			
		}
	}
	
	/**
	 * stop listening to the layer, if necessary
	 */
	void clearLayerListener()
	{
		if (_myLayers != null)
		{
			_myLayers.removeDataExtendedListener(_myLayersListener);
			_myLayersListener = null;
			_myLayers = null;
		}
	}
	
	private void listenToMyParts()
	{
		// Listen to Layers
		_myPartMonitor.addPartListener(Layers.class, PartMonitor.ACTIVATED,
				new PartMonitor.ICallback()
				{
					public void eventTriggered(String type, Object part,
							IWorkbenchPart parentPart)
					{
						processNewLayers(part);
					}
				});
		_myPartMonitor.addPartListener(Layers.class, PartMonitor.OPENED,
				new PartMonitor.ICallback()
				{
					public void eventTriggered(String type, Object part,
							IWorkbenchPart parentPart)
					{
						processNewLayers(part);
					}
				});
		_myPartMonitor.addPartListener(Layers.class, PartMonitor.CLOSED,
				new PartMonitor.ICallback()
				{
					public void eventTriggered(String type, Object part,
							IWorkbenchPart parentPart)
					{
						// is this our set of layers?
						if (part == _myLayers)
						{
							// stop listening to this layer
							clearLayerListener();
						}						
					}

				});
		
		_myPartMonitor.addPartListener(TimeController.class, PartMonitor.ACTIVATED,
				new PartMonitor.ICallback()
				{
					public void eventTriggered(String type, Object part,
							IWorkbenchPart parentPart)
					{
						TimeProvider provider = ((TimeController) part).getTimeProvider();						
						if (provider != null && provider.equals(_timeProvider))
							return;						
						_timeProvider = provider;
						_timeProvider.addListener(_temporalListener, 
								TimeProvider.TIME_CHANGED_PROPERTY_NAME);
					}
			});
		_myPartMonitor.addPartListener(TimeController.class, PartMonitor.OPENED,
				new PartMonitor.ICallback()
				{
					public void eventTriggered(String type, Object part,
							IWorkbenchPart parentPart)
					{
						TimeProvider provider = ((TimeController) part).getTimeProvider();						
						if (provider != null && provider.equals(_timeProvider))
							return;						
						_timeProvider = provider;
						_timeProvider.addListener(_temporalListener, 
								TimeProvider.TIME_CHANGED_PROPERTY_NAME);
					}
			});
		
		_myPartMonitor.addPartListener(TimeController.class, PartMonitor.CLOSED,
				new PartMonitor.ICallback()
				{
					public void eventTriggered(String type, Object part,
							IWorkbenchPart parentPart)
					{
						TimeProvider provider = ((TimeController) part).getTimeProvider();						
						if (provider != null && provider.equals(_timeProvider))
						{
							_timeProvider.removeListener(_temporalListener, 
									TimeProvider.TIME_CHANGED_PROPERTY_NAME);
						}
					}

				});
		
		
		
		
		_myPartMonitor.addPartListener(ISelectionProvider.class,
				PartMonitor.ACTIVATED, new PartMonitor.ICallback()
				{
					public void eventTriggered(String type, Object part,
							IWorkbenchPart parentPart)
					{
						// aah, just check it's not is
						if (part != _viewer)
						{
							ISelectionProvider iS = (ISelectionProvider) part;
							iS.addSelectionChangedListener(_selectionChangeListener);
						}
					}
				});
		_myPartMonitor.addPartListener(ISelectionProvider.class,
				PartMonitor.DEACTIVATED, new PartMonitor.ICallback()
				{
					public void eventTriggered(String type, Object part,
							IWorkbenchPart parentPart)
					{
						// aah, just check it's not is
						if (part != _viewer)
						{
							ISelectionProvider iS = (ISelectionProvider) part;
							iS.removeSelectionChangedListener(_selectionChangeListener);
						}
					}
				});

		// ok we're all ready now. just try and see if the current part is valid
		_myPartMonitor.fireActivePart(getSite().getWorkbenchWindow()
				.getActivePage());
	}
	
	public void editableSelected(ISelection sel, EditableWrapper pw) 
	{

		// ahh, just check if this is a whole new layers object
		if (pw.getEditable() instanceof Layers) 
		{
			processNewLayers(pw.getEditable());
			return;
		}

		// just check that this is something we can work with
		if (sel instanceof StructuredSelection) 
		{
			StructuredSelection str = (StructuredSelection) sel;

			// hey, is there a payload?
			if (str.getFirstElement() != null) 
			{
				// sure is. we only support single selections, so get the first
				// element
				Object first = str.getFirstElement();
				if (first instanceof EditableWrapper) 
					_viewer.setSelectionToWidget((StructuredSelection) sel);				
			}
		}

	}

	@SuppressWarnings("rawtypes")
	public Object getAdapter(Class adapter)
	{
		Object res = null;

		if (adapter == ISelectionProvider.class)
		{
			res = _viewer;
		}
		else
		{
			res = super.getAdapter(adapter);
		}

		return res;
	}
	
	protected final class NewTimeListener implements PropertyChangeListener
	{
		public void propertyChange(PropertyChangeEvent event)
		{
			// see if it's the time or the period which
			// has changed
			if (event.getPropertyName().equals(
					TimeProvider.TIME_CHANGED_PROPERTY_NAME))
			{
				// ok, use the new time
				final HiResDate newDTG = (HiResDate) event.getNewValue();
				final HiResDate oldDTG = (HiResDate) event.getOldValue();
				Runnable nextEvent = new Runnable()
				{
					public void run()
					{
						_viewer._painter.drawDebriefTime(oldDTG.getDate(), newDTG.getDate());
					}
				};
				Display.getDefault().syncExec(nextEvent);				
			}
		}
	}

}