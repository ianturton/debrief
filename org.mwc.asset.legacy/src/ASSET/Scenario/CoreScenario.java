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

package ASSET.Scenario;

import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;

import ASSET.NetworkParticipant;
import ASSET.ParticipantType;
import ASSET.ScenarioType;
import ASSET.Models.Detection.DetectionEvent;
import ASSET.Models.Environment.EnvironmentType;
import ASSET.Models.Environment.SimpleEnvironment;
import ASSET.Models.Sensor.Initial.BroadbandSensor;
import ASSET.Models.Vessels.SSN;
import ASSET.Participants.Status;
import ASSET.Scenario.LiveScenario.ISimulation;
import ASSET.Util.RandomGenerator;
import MWC.GUI.BaseLayer;
import MWC.GUI.Layer;
import MWC.GenericData.Duration;
import MWC.GenericData.WorldLocation;
import MWC.GenericData.WorldSpeed;

public class CoreScenario implements ScenarioType, ISimulation
{

	// ////////////////////////////////////
	// objects
	// ////////////////////////////////////

	/**
	 * The list of participants we maintain
	 */
	HashMap<Integer, ParticipantType> _myVisibleParticipants = new HashMap<Integer, ParticipantType>();

	/**
	 * The list of monte carlo participants we maintain
	 */
	HashMap<Integer, ParticipantType> _myInvisibleParticipants = new HashMap<Integer, ParticipantType>();

	/**
	 * the complete list of participants we store
	 */
	HashMap<Integer, ParticipantType> _completeParticipantList = new HashMap<Integer, ParticipantType>();

	/**
	 * the list of any participants which have been destroyed during a step - they
	 * are only actually removed at the end of the step - we store the index of
	 * any to be destroyed
	 */
	private Vector<Integer> _pendingDestruction = new Vector<Integer>(0, 1);

	/**
	 * the list of any participants which have been created during a step - they
	 * are only actually added at the end of the step - we store the participant
	 * itself
	 */
	private Vector<ParticipantType> _pendingCreation = new Vector<ParticipantType>(
			0, 1);

	/**
	 * the name of this scenario
	 */
	private String _myName;

	/**
	 * the case id of this scenario. The case id is used by multiple scenario
	 * generation algorithms to identify which permutation this scenario
	 * represents.
	 */
	private String _myCaseId;

	/**
	 * the step time for this scenario (millis)
	 */
	private int _myStepTime = 0;

	/**
	 * the elapsed time for this scenario (millis)
	 */
	long _myTime = 0;

	/**
	 * the initial value of time for this scenario
	 */
	private long _myStartTime = 0;

	/**
	 * the scenario time step for this scenario (millis)
	 */
	int _myScenarioStepTime = 1000;

	/**
	 * the list of stepping listeners
	 */
	private Vector<ScenarioSteppedListener> _stepListeners;

	/**
	 * the list of participants changed listeners
	 */
	private Vector<ParticipantsChangedListener> _participantListeners;

	/**
	 * the list of running listeners
	 */
	private Vector<ScenarioRunningListener> _runningListeners;

	/**
	 * the timer we are using
	 */
	private javax.swing.Timer _myTimer;

	/**
	 * property change listener support
	 */
	private java.beans.PropertyChangeSupport _pSupport;

	/**
	 * the environment type for our model
	 */
	private EnvironmentType _myEnvironment;

	/**
	 * the system time when we started running (to provide a report on how long it
	 * took..
	 */
	private long _systemStartTime = 0;

	/**
	 * a seed to set when first starting a scenario (or null to continue with a
	 * random series of randoms
	 */
	private Integer _mySeed = null;

	/**
	 * if we have been stopped, this is the reason why It's also a flag to
	 * indicate that somebody has triggered a scenario stop - note that we only
	 * process the stop at the end of a step
	 */
	protected String _stopReason = null;

	// ////////////////////////////////////
	// constructor
	// ////////////////////////////////////
	public CoreScenario()
	{
		// initialise the timer
		_myTimer = new javax.swing.Timer(0, new java.awt.event.ActionListener()
		{
			private boolean stepping = false;

			public void actionPerformed(java.awt.event.ActionEvent e)
			{
				if (stepping)
					System.err.println("STEPPING");

				stepping = true;
				step();
				stepping = false;
			}
		});

		_pSupport = new java.beans.PropertyChangeSupport(this);

		// create the environment
		_myEnvironment = createEnvironment();

		// give the scenario a random case id
		_myCaseId = "Case_" + (int) (Math.random() * 2000d);

		// initialise the display settings
		_displaySettings = new HashMap<String, String>();

	}

	// ////////////////////////////////////
	// methods
	// ////////////////////////////////////
	private EnvironmentType createEnvironment()
	{
		return new SimpleEnvironment(1, 1, 1);
	}

	/**
	 * Start the scenario auto-stepping through itself. If the step time is set to
	 * zero, it will automatically step after the completion of the previous step.
	 */
	public void start()
	{
		// store the delay time
		_myTimer.setDelay(_myStepTime);

		// initialse the seed (if we have one)
		if (_mySeed != null)
			RandomGenerator.seed(_mySeed.intValue());

		// store the system start time
		_systemStartTime = System.currentTimeMillis();

		// fire a stepped event to let the recorders store the initial state		
		// No, don't - it's causing the status to go out twice
		// fireScenarioStepped(this.getTime());

		if (!_myTimer.isRunning())
		{
			// get it going
			_myTimer.start();
		}

		this.fireScenarioStarted();

	}

	/**
	 * find out if the timer is currently auto-stepping
	 * 
	 * @return
	 */
	public boolean isRunning()
	{
		return _myTimer.isRunning();
	}

	/**
	 * let anybody request that the scenario stop, though note that we only
	 * process this at the end of a step.
	 * <p/>
	 * It's the doStop method which actually stops us
	 * 
	 * @param reason
	 */
	public void stop(String reason)
	{
		_stopReason = reason;
	}

	/**
	 * pause the automatic execution (but don't assume that the scenario is
	 * complete)
	 */
	public void pause()
	{
		// is timer running
		if (_myTimer.isRunning())
			_myTimer.stop();

		// fire event
		this.fireScenarioPaused();
	}

	/**
	 * Stop the scenario from auto-stepping
	 */
	private void doStop()
	{
		// remember the reason
		String thisReason = _stopReason;

		// forget our local one, to be sure we aren't called twice
		_stopReason = null;

		// and store how long it took
		long finishTime = System.currentTimeMillis();

		// is timer running
		if (_myTimer.isRunning())
			_myTimer.stop();

		// work out how long it took
		long elapsedTime = finishTime - _systemStartTime;

		// fire event
		this.fireScenarioStopped(elapsedTime, thisReason);

		System.out.println("Scenario stopped:" + thisReason);
	}

	boolean _firstPass = true;

	/**
	 * the information we plot as a backdrop
	 * 
	 */
	private BaseLayer _myBackdrop;

	private HashMap<String, String> _displaySettings;

	/**
	 * Move the scenario through a single step
	 */
	public void step()
	{

		// process a stop, if anybody has asked for it.
		if (_stopReason != null)
		{
			doStop();

			// and now drop out
			return;
		}

		long oldTime = _myTime;

		if (!_firstPass)
		{
			// move time forward
			_myTime += _myScenarioStepTime;
		}

		// move the participants forward
		if (_completeParticipantList != null)
		{

			_firstPass = false;

			// ////////////////////////////////////////////////
			// first the decision cycle
			// ////////////////////////////////////////////////
			java.util.Iterator<ParticipantType> iter = _completeParticipantList
					.values().iterator();
			while (iter.hasNext())
			{
				final ParticipantType pt = (ParticipantType) iter.next();
				
				// note: we aren't using the isAlive test to decide whether to do decisions,
				// since it's the decision that brings it to life
				pt.doDecision(oldTime, _myTime, this);
			}

			// ////////////////////////////////////////////////
			// now the movement cycle
			// ////////////////////////////////////////////////
			iter = _completeParticipantList.values().iterator();
			while (iter.hasNext())
			{
				final ParticipantType pt = (ParticipantType) iter.next();
				if (pt.isAlive())
					pt.doMovement(oldTime, _myTime, this);
			}

			// ////////////////////////////////////////////////
			// now the detection cycle
			// ////////////////////////////////////////////////
			HashMap<Integer, ParticipantType> copiedList = new HashMap<Integer, ParticipantType>(
					_completeParticipantList);
			iter = copiedList.values().iterator();
			while (iter.hasNext())
			{
				final ParticipantType pt = (ParticipantType) iter.next();
				if (pt.isAlive())
					pt.doDetection(oldTime, _myTime, this);
			}
		}

		// now add any recently created participants
		for (int ic = 0; ic < this._pendingCreation.size(); ic++)
		{
			final ParticipantType thisPart = (ParticipantType) this._pendingCreation
					.elementAt(ic);
			addParticipant(thisPart.getId(), thisPart);
		}
		// and clear the list
		_pendingCreation.clear();

		// finally, delete the destroyed participants
		for (int i = 0; i < this._pendingDestruction.size(); i++)
		{
			final Integer thisIndex = (Integer) this._pendingDestruction.elementAt(i);
			removeParticipant(thisIndex.intValue());
		}

		// and clear the list waiting to be destroyed
		_pendingDestruction.clear();

		// fire messages
		this.fireScenarioStepped(_myTime);

	}

	/**
	 * restart the scenario
	 */
	public void restart()
	{
		// reset our local time values
		_myTime = _myStartTime;

		// clear the 'dead' flag
		_stopReason = null;

		// step through the participants
		// move the participants forward
		if (_completeParticipantList != null)
		{
			final java.util.Iterator<ParticipantType> iter = _completeParticipantList
					.values().iterator();
			while (iter.hasNext())
			{
				final ParticipantType pt = iter.next();
				pt.restart(this);
			}
		}

		// reset the observers

		// reset the listeners
		if (_participantListeners != null)
		{
			Vector<ParticipantsChangedListener> tmpList = new Vector<ParticipantsChangedListener>(
					_participantListeners);
			final Iterator<ParticipantsChangedListener> it = tmpList.iterator();
			while (it.hasNext())
			{
				final ParticipantsChangedListener pcl = it.next();
				pcl.restart(this);
			}
		}

		if (_runningListeners != null)
		{
			// work with a copy of the running listeners, to prevent concurrent
			// changes (since in the restart
			// a listener may remove then re-insert itself as a listener
			Vector<ScenarioRunningListener> copyRunningListeners = new Vector<ScenarioRunningListener>(
					_runningListeners);
			final Iterator<ScenarioRunningListener> it = copyRunningListeners
					.iterator();
			while (it.hasNext())
			{
				final ScenarioRunningListener prl = it.next();
				prl.restart(this);
			}
		}

		if (_stepListeners != null)
		{
			final Vector<ScenarioSteppedListener> copyStepListeners = new Vector<ScenarioSteppedListener>(
					_stepListeners);
			final Iterator<ScenarioSteppedListener> it = copyStepListeners.iterator();
			while (it.hasNext())
			{
				final ScenarioSteppedListener ssl = it.next();
				ssl.restart(this);
			}
		}

	}

	/**
	 * @return the scenario time step
	 */
	public int getScenarioStepTime()
	{
		return _myScenarioStepTime;
	}

	/**
	 * set the scenario time step
	 * 
	 * @param step_size
	 *          millis to step scenario forward at each step
	 */
	public void setScenarioStepTime(final int step_size)
	{
		_myScenarioStepTime = step_size;

		// fire the new value
		fireNewScenarioStepSize(step_size);

	}

	/**
	 * set the scenario time step
	 * 
	 * @param step_size
	 *          time to step scenario forward at each step
	 */
	public void setScenarioStepTime(Duration step_size)
	{
		this.setScenarioStepTime((int) step_size.getValueIn(Duration.MILLISECONDS));
	}

	/**
	 * set the size of the time delay (or zero to run to completion)
	 * 
	 * @param step_size
	 *          time to pause before step (or zero to run)
	 */
	public void setStepTime(Duration step_size)
	{
		this.setStepTime((int) step_size.getValueIn(Duration.MILLISECONDS));
	}

	/**
	 * @return the step time for auto-stepping (or zero to run on)
	 */
	public int getStepTime()
	{
		return _myStepTime;
	}

	/**
	 * set the size of the time delay (or zero to run to completion)
	 * 
	 * @param step_size
	 *          millis to pause before step (or zero to run)
	 */
	public void setStepTime(final int step_size)
	{
		// store the new value
		_myStepTime = step_size;

		// update the timer
		_myTimer.setDelay(step_size);

		// fire the new value
		fireNewStepSize(step_size);
	}

	/**
	 * get the name of this scenaro
	 */
	public String getName()
	{
		return _myName;
	}

	/**
	 * set the name of this scenario
	 */
	public void setName(final String val)
	{
		// store old value

		String oldVal = null;
		if (_myName != null)
			oldVal = new String(_myName);

		// fire the event
		_pSupport.firePropertyChange(NAME, oldVal, val);

		// do the update
		_myName = val;

	}

	/**
	 * set the current time of the scenario (millis)
	 */
	public long getTime()
	{
		return _myTime;
	}

	/**
	 * set the initial time of the scenario (millis)
	 */
	public void setTime(final long time)
	{
		_myTime = time;
		_myStartTime = time;
	}

	/**
	 * set the seed to use for running this scenario
	 */
	public void setSeed(Integer seed)
	{
		_mySeed = seed;
	}

	public String getCaseId()
	{
		return _myCaseId;
	}

	public void setCaseId(String myCaseId)
	{
		this._myCaseId = myCaseId;
	}

	/**
	 * get the environment for this model
	 */
	public EnvironmentType getEnvironment()
	{
		return _myEnvironment;
	}

	/**
	 * and set the environment.
	 */
	public void setEnvironment(EnvironmentType theEnv)
	{
		_myEnvironment = theEnv;
	}

	/**
	 * Shut down this scenario. Close the participants, etc
	 */
	public void close()
	{
		//
		_myEnvironment = null;
		if (_myBackdrop != null)
			_myBackdrop.removeAllElements();
		if (_myInvisibleParticipants != null)
			_myInvisibleParticipants.clear();
		if (_myVisibleParticipants != null)
			_myVisibleParticipants.clear();
		_myName = "empty";
	}

	// ///////////////////////////////////////////////////
	// manage our listeners
	// ///////////////////////////////////////////////////
	public void addParticipantsChangedListener(
			final ParticipantsChangedListener list)
	{
		if (_participantListeners == null)
			_participantListeners = new Vector<ParticipantsChangedListener>(1, 2);

		_participantListeners.add(list);
	}

	public void removeParticipantsChangedListener(
			final ParticipantsChangedListener list)
	{
		_participantListeners.remove(list);
	}

	public void addPropertyChangeListener(final String property_name,
			final PropertyChangeListener listener)
	{
		if (property_name == null)
			_pSupport.addPropertyChangeListener(listener);
		else
			_pSupport.addPropertyChangeListener(property_name, listener);
	}

	public void removePropertyChangeListener(final String property_name,
			final PropertyChangeListener listener)
	{
		try
		{
			if (property_name == null)
				_pSupport.removePropertyChangeListener(listener);
			else
				_pSupport.removePropertyChangeListener(property_name, listener);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public void addScenarioRunningListener(final ScenarioRunningListener listener)
	{
		if (_runningListeners == null)
			_runningListeners = new Vector<ScenarioRunningListener>(1, 2);

		// right, insert the new running listener at the head of the chain. This is
		// to overcome a problem with
		// the Loader class - which adds itself as a scenario listener before it's
		// children. When we STOP
		// the loader goes in and calls all of the tearDown methods for observers
		// which it has loaded, so ditching the _myScenario object - even though the
		// child observer actually
		// may want to listen out for the Stop event itself in order to output it's
		// batch (or other) data
		_runningListeners.add(0, listener);
	}

	public void removeScenarioRunningListener(
			final ScenarioRunningListener listener)
	{
		_runningListeners.remove(listener);
	}

	public void addScenarioSteppedListener(final ScenarioSteppedListener listener)
	{
		if (_stepListeners == null)
			_stepListeners = new Vector<ScenarioSteppedListener>(1, 2);

		_stepListeners.add(listener);
	}

	public void removeScenarioSteppedListener(
			final ScenarioSteppedListener listener)
	{
		_stepListeners.remove(listener);
	}

	private void fireParticipantChanged(final int index, final boolean added)
	{
		if (_participantListeners != null)
		{
			final Iterator<ParticipantsChangedListener> it = _participantListeners
					.iterator();
			while (it.hasNext())
			{
				final ParticipantsChangedListener pcl = (ParticipantsChangedListener) it
						.next();
				if (added)
					pcl.newParticipant(index);
				else
					pcl.participantRemoved(index);
			}
		}
	}

	void fireScenarioStepped(final long time)
	{
		if (_stepListeners != null)
		{
			// take copy of step listeners, in case it gets modified mid-step
			final Vector<ScenarioSteppedListener> copyStepListeners = new Vector<ScenarioSteppedListener>(
					_stepListeners);
			final Iterator<ScenarioSteppedListener> it = copyStepListeners.iterator();
			while (it.hasNext())
			{
				final ScenarioSteppedListener pcl = (ScenarioSteppedListener) it.next();
				try
				{
					pcl.step(this, time);
				}
				catch (Exception e)
				{
					System.out.println("time:" + time + " pcl was:" + pcl);
					e.printStackTrace(); // To change body of catch statement use Options
																// | File Templates.
				}
			}
		}
	}

	private void fireScenarioStarted()
	{

		if (_runningListeners != null)
		{
			final Iterator<ScenarioRunningListener> it = _runningListeners.iterator();
			while (it.hasNext())
			{
				final ScenarioRunningListener pcl = (ScenarioRunningListener) it.next();
				pcl.started();
			}
		}

	}

	/**
	 * pass the message out to the listeners
	 * 
	 * @param timeTaken
	 *          how long we ran for
	 * @param reason
	 *          the reason for stopping
	 */
	private void fireScenarioStopped(final long timeTaken, String reason)
	{

		if (_runningListeners != null)
		{
			// take a deep copy of the running listeners - when we're cycling through
			// the list there's the chance that
			// a listener may try and remove itself. bugger
			Vector<ScenarioRunningListener> copyListeners = new Vector<ScenarioRunningListener>(
					0, 1);
			copyListeners.addAll(_runningListeners);

			final Iterator<ScenarioRunningListener> it = copyListeners.iterator();
			while (it.hasNext())
			{
				final ScenarioRunningListener pcl = it.next();
				pcl.finished(timeTaken, reason);
			}

			// and ditch our spare copy.
			copyListeners.removeAllElements();
			copyListeners = null;
		}
	}

	/**
	 * pass the message out to the listeners
	 */
	private void fireScenarioPaused()
	{

		if (_runningListeners != null)
		{
			final Iterator<ScenarioRunningListener> it = _runningListeners.iterator();
			while (it.hasNext())
			{
				final ScenarioRunningListener pcl = (ScenarioRunningListener) it.next();
				pcl.paused();
			}
		}
	}

	private void fireNewStepSize(final int val)
	{

		if (_runningListeners != null)
		{
			final Iterator<ScenarioRunningListener> it = _runningListeners.iterator();
			while (it.hasNext())
			{
				final ScenarioRunningListener pcl = (ScenarioRunningListener) it.next();
				pcl.newStepTime(val);
			}
		}
	}

	private void fireNewScenarioStepSize(final int val)
	{

		if (_runningListeners != null)
		{
			final Iterator<ScenarioRunningListener> it = _runningListeners.iterator();
			while (it.hasNext())
			{
				final ScenarioRunningListener pcl = (ScenarioRunningListener) it.next();
				pcl.newScenarioStepTime(val);
			}
		}
	}

	// //////////////////////////////////////////////////////
	// participant-related
	// //////////////////////////////////////////////////////
	/**
	 * Return a particular Participant - so that the Participant can be controlled
	 * directly. Listeners added/removed. Participants added/removed, etc.
	 */
	public ParticipantType getThisParticipant(final int id)
	{
		ParticipantType res = null;
		if (_completeParticipantList != null)
			res = (ParticipantType) _completeParticipantList.get(new Integer(id));

		return res;
	}

	/**
	 * Provide a list of id numbers of Participant we contain
	 * 
	 * @return list of ids of Participant we contain
	 */
	public Integer[] getListOfParticipants()
	{
		Integer[] res = new Integer[0];

		if (_completeParticipantList != null)
		{
			final java.util.Collection<Integer> vals = _completeParticipantList
					.keySet();
			res = vals.toArray(res);
		}

		return res;
	}

	/**
	 * Provide a list of id numbers of Participant we contain
	 * 
	 * @return list of ids of Participant we contain
	 */
	public Collection<ParticipantType> getListOfVisibleParticipants()
	{
		Collection<ParticipantType> res = null;

		if (_myVisibleParticipants != null)
		{
			res = _myVisibleParticipants.values();
		}

		return res;
	}

	/**
	 * convenience method for adding a participant to a hashmap
	 */
	private void addParticipantToThisList(int index,
			final ASSET.ParticipantType participant,
			HashMap<Integer, ParticipantType> map)
	{
		// if the index is zero, we will create one
		if (index == INVALID_ID)
		{
			index = ASSET.Util.IdNumber.generateInt();

			// put this index into the state, if there is one
			if (participant.getStatus() != null)
				participant.getStatus().setId(index);
		}

		// do we contain this one already
		if (map.get(new Integer(index)) != null)
			System.err.println("DUPLICATE ENTITY BEING ADDED:!" + index);

		// store it
		map.put(new Integer(index), participant);

	}

	/**
	 * back door, for reloading a scenario from file)
	 */
	public void addParticipant(int index, final ASSET.ParticipantType participant)
	{
		addParticipantToThisList(index, participant, _myVisibleParticipants);

		// and the complete list
		addParticipantToThisList(index, participant, _completeParticipantList);

		// fire new scenario event
		this.fireParticipantChanged(index, true);
	}

	/**
	 * clear out the participants
	 * 
	 */
	public void emptyParticipants()
	{
		// get their indices
		Integer[] parts = getListOfParticipants();
		for (int i = 0; i < parts.length; i++)
		{
			removeParticipant(parts[i]);
		}
	}

	/**
	 * handle inserting a monte carlo participant
	 * 
	 * @param index
	 * @param participant
	 */
	public void addMonteCarloParticipant(int index,
			final ASSET.ParticipantType participant)
	{
		addParticipantToThisList(index, participant, _myInvisibleParticipants);

		// and the complete list
		addParticipantToThisList(index, participant, _completeParticipantList);

		// fire new scenario event
		this.fireParticipantChanged(index, true);
	}

	/**
	 * remove the indicated participant
	 */
	public void removeParticipant(final int index)
	{
		final Object found = _completeParticipantList.remove(new Integer(index));

		// fire new scenario event
		if (found != null)
		{
			// try to remove it from the other lists
			_myVisibleParticipants.remove(new Integer(index));
			_myInvisibleParticipants.remove(new Integer(index));

			// and fire the informant
			this.fireParticipantChanged(index, false);
		}
	}

	/**
	 * Create a new Participant. The external client can then request the
	 * Participant itself to perform any edits
	 * 
	 * @param participant_type
	 *          the type of Participant the client wants
	 * @return the id of the new Participant (or INVALID_ID for failure)
	 */
	public int createNewParticipant(final String participant_type)
	{
		// what we should do, is to create the scenario from the scenario type name,
		// using the specified scenario_type in the classloader

		// get the id
		int id = ASSET.Util.IdNumber.generateInt();

		// create the correct type of participant
		ParticipantType newS = null;
		if (participant_type.equals(ASSET.Participants.Category.Type.SUBMARINE))
			newS = new ASSET.Models.Vessels.SSN(id);
		else if (participant_type.equals(ASSET.Participants.Category.Type.FRIGATE))
		{
			newS = new ASSET.Models.Vessels.Surface(id);
			newS.getCategory().setType(ASSET.Participants.Category.Type.FRIGATE);
		}
		else if (participant_type
				.equals(ASSET.Participants.Category.Type.DESTROYER))
		{
			newS = new ASSET.Models.Vessels.Surface(id);
			newS.getCategory().setType(ASSET.Participants.Category.Type.DESTROYER);
		}
		else if (participant_type.equals("SSK"))
			newS = new ASSET.Models.Vessels.SSK(id);

		// check it worked
		if (newS != null)
		{
			// give it a default name
			newS.setName(participant_type + "_" + id);

			// add it
			addParticipant(id, newS);
		}
		else
		{
			MWC.Utilities.Errors.Trace.trace("Vessel type not matched");
			id = INVALID_ID;
		}

		// return it's index
		return id;
	}

	/**
	 * the detonation event itself
	 * 
	 * @param loc
	 *          the location of the detonation
	 * @param power
	 *          the strength of the detonation
	 */
	public void detonationAt(int WeaponID, final WorldLocation loc, double power)
	{
		// @@ CREATE EXPLOSION MODEL

		// see if any participants are within some explosion radius (500 yds)
		final double EXPLOSION_RADIUS = 500;

		// move the participants forward
		if (_completeParticipantList != null)
		{
			final java.util.Iterator<ParticipantType> iter = _completeParticipantList
					.values().iterator();
			while (iter.hasNext())
			{
				final NetworkParticipant pt = (NetworkParticipant) iter.next();
				// how far away is it?
				final double rng_degs = pt.getStatus().getLocation().rangeFrom(loc);
				final double rng_yds = MWC.Algorithms.Conversions.Degs2Yds(rng_degs);
				if (rng_yds < EXPLOSION_RADIUS)
				{
					this._pendingDestruction.add(new Integer(pt.getId()));
				}
			}
		}

	}

	/**
	 * method to add a new participant
	 * 
	 * @param newPart
	 *          the new participant
	 */
	public void createParticipant(final ParticipantType newPart)
	{
		this._pendingCreation.add(newPart);
	}

	// ////////////////////////////////////////////////////////////////////////////////////////////////
	// testing for this class
	// ////////////////////////////////////////////////////////////////////////////////////////////////
	public static class ScenarioTest extends junit.framework.TestCase
	{
		static public final String TEST_ALL_TEST_TYPE = "UNIT";

		public ScenarioTest(final String val)
		{
			super(val);
		}

		protected int stepCounter = 0;
		protected long lastTime = 0;
		protected Boolean lastStartState = null;
		protected int createdCounter = 0;
		protected int destroyedCounter = 0;

		protected class createdListener implements ParticipantsChangedListener
		{
			/**
			 * the indicated participant has been added to the scenario
			 */
			public void newParticipant(int index)
			{
				createdCounter++;
			}

			/**
			 * the indicated participant has been removed from the scenario
			 */
			public void participantRemoved(int index)
			{
				destroyedCounter++;
			}

			public void restart(ScenarioType scenario)
			{

			}
		}

		int newStepTime;

		protected class startStopListener implements ScenarioRunningListener
		{
			public void started()
			{
				lastStartState = new Boolean(true);
			}

			/**
			 * the scenario has stopped running on auto
			 */
			public void paused()
			{
				// To change body of implemented methods use File | Settings | File
				// Templates.
			}

			public void finished(long elapsedTime, String reason)
			{
				lastStartState = new Boolean(false);
			}

			public void newScenarioStepTime(final int val)
			{
				newStepTime = val;
			}

			public void newStepTime(final int val)
			{
				newStepTime = val;
			}

			public void restart(ScenarioType scenario)
			{

			}

		}

		protected class stepListener implements ScenarioSteppedListener
		{
			public void step(ScenarioType scenario, final long newTime)
			{
				lastTime = newTime;
				stepCounter++;
			}

			public void restart(ScenarioType scenario)
			{

			}

		}

		public void testScenarioTimes()
		{
			final ScenarioType cs = new CoreScenario();

			// listener
			final stepListener sl = new stepListener();
			final startStopListener ssl = new startStopListener();
			cs.addScenarioSteppedListener(sl);
			cs.addScenarioRunningListener(ssl);

			// step times
			cs.setStepTime(new Duration(5, Duration.SECONDS));
			assertEquals("New step time", 5000, cs.getStepTime());
			assertEquals("New step time event", 5000, newStepTime);
			cs.setScenarioStepTime(new Duration(4, Duration.SECONDS));
			assertEquals("New scenario step time", 4000, cs.getScenarioStepTime());
			assertEquals("New scenario step time event", 4000, newStepTime);

			cs.setStepTime(500);
			assertEquals("New step time", 500, cs.getStepTime());
			assertEquals("New step time event", 500, newStepTime);
			cs.setScenarioStepTime(1000);
			assertEquals("New scenario step time", 1000, cs.getScenarioStepTime());
			assertEquals("New scenario step time event", 1000, newStepTime);

			// current time
			cs.setTime(100);
			assertEquals("set time", 100, cs.getTime());

			// stepping - firing zero time event first
			cs.step();
			assertEquals("fired at zero time", 100, cs.getTime());

			// stepping
			cs.step();
			assertEquals("first step", 1100, cs.getTime());

			cs.step();
			assertEquals("second step", 2100, cs.getTime());
			assertEquals("second step message", 2100, lastTime);
			assertEquals("step message count", 3, stepCounter);

			// start
			// time step interval
			cs.setStepTime(1000);

			// reset the step counter
			stepCounter = 0;

			// check our listener is prepped
			assertNull("Start-Stop listener is ready", lastStartState);
			System.out.println("about to start timed steps");

			long tStart = System.currentTimeMillis();
			cs.start();

			assertTrue("Start message sent", (lastStartState.booleanValue() == true));

			while (stepCounter < 3)
			{
				try
				{
					Thread.sleep(100);
				}
				catch (java.lang.InterruptedException e)
				{
					e.printStackTrace();
				}
			}

			long tEnd = System.currentTimeMillis();
			System.out.println("finished timed steps");

			CoreScenario coreS = (CoreScenario) cs;
			assertEquals("Stop not pending yet", null, coreS._stopReason);

			cs.stop("testing");

			assertTrue("Stop message sent but not processed",
					(lastStartState.booleanValue() == true));
			assertNotNull("Stop message sent but not processed", coreS._stopReason);

			// insert a step, so the stop can be processed
			cs.step();

			assertTrue("Stop message sent", (lastStartState.booleanValue() == false));

			long tDiff = tEnd - tStart;

			// check time elapsed
			assertTrue("timer actually ran: time was:" + tDiff, (tDiff >= 1000));
			assertTrue("timer didn't take too long" + tDiff, (tDiff < 2300));

			// running on auto!
			cs.setStepTime(0);
			cs.setScenarioStepTime(5);
			cs.setTime(0);

			tStart = System.currentTimeMillis();

			// reset the step counter
			stepCounter = 0;

			// check our listener is prepped
			lastStartState = null;
			assertNull("Start-Stop listener is ready", lastStartState);

			// get going
			cs.start();
			assertTrue("Start message sent", (lastStartState.booleanValue() == true));

			while (stepCounter < 100)
			{
				try
				{
					Thread.sleep(1);
				}
				catch (java.lang.InterruptedException e)
				{
					e.printStackTrace();
				}
			}
			cs.stop("testing");

			tEnd = System.currentTimeMillis();

			cs.step();

			assertTrue("Stop message sent", (lastStartState.booleanValue() == false));

			// how long?
			tDiff = tEnd - tStart;

			// check time elapsed

			// hack: suspect both of these test should allow zero error.
	//		assertEquals("steps got performed", 140, stepCounter, 40);
	//		assertEquals("scenario moved forward", 600, cs.getTime(), 100);

			// stop
			// - this has already been tested

			// reset
		}

		public void testScenarioParticipants()
		{

			// initialise our test counters
			createdCounter = 0;
			destroyedCounter = 0;

			// create server
			final ScenarioType srv = new CoreScenario();

			// add as listener
			final createdListener cl = new createdListener();
			srv.addParticipantsChangedListener(cl);

			// create new scenarios
			// final int s1 = srv.createNewParticipant("SUBMARINE");
			final int s2 = srv.createNewParticipant("FRIGATE");

			// now stop listening for these events
			srv.removeParticipantsChangedListener(cl);

			// add another, which we shouldn't hear about
			final int s3 = srv.createNewParticipant("DESTROYER");

			// check events got fired
			assertEquals("count of created events (one ignored)", 1, createdCounter);
			assertEquals("count of destroyed events", destroyedCounter, 0);

			// get list of scenarios
			final Integer[] res = srv.getListOfParticipants();

			assertEquals("Wrong number of participants", res.length, 2);

			// get specific scenarios
			final ParticipantType sc2 = srv.getThisParticipant(s2);
			assertTrue("participant returned", sc2 != null);

			if (sc2 == null)
				return;

			// make edits
			sc2.setName("participant 2");
			final ParticipantType sc3 = srv.getThisParticipant(s3);
			sc3.setName("participant 3");

			// re-retrieve this scenario to check we're getting the correct one
			final NetworkParticipant sc2a = srv.getThisParticipant(s2);
			assertEquals("correct name", sc2a.getName(), "participant 2");

			// check we can remove a participant
			// , but check the length first
			final int len = srv.getListOfParticipants().length;
			srv.removeParticipant(s2);

			// check the length
			assertEquals("check participant removed", len - 1,
					srv.getListOfParticipants().length);

			// check invalid scenario indices
			NetworkParticipant dd = srv.getThisParticipant(1000);
			assertEquals("invalid index", dd, null);
			dd = srv.getThisParticipant(0);
			assertEquals("invalid index", dd, null);
		}

		public void testMonteCarloRunning()
		{
			SSN ssn_A = new SSN(12);
			SSN ssn_B = new SSN(13);
			SSN ssn_C = new SSN(14);
			SSN ssn_D = new SSN(15);

			Status theStat = new Status(12, 0);
			WorldLocation theLoc = new WorldLocation(1, 1, 1);
			theStat.setSpeed(new WorldSpeed(12, WorldSpeed.Kts));
			theStat.setLocation(theLoc);

			ssn_A.setStatus(theStat);
			ssn_B.setStatus(theStat);
			ssn_C.setStatus(theStat);
			ssn_D.setStatus(theStat);

			CoreScenario scen = new CoreScenario();

			scen.addParticipant(ssn_A.getId(), ssn_A);
			scen.addParticipant(ssn_B.getId(), ssn_B);
			scen.addMonteCarloParticipant(ssn_C.getId(), ssn_C);
			scen.addMonteCarloParticipant(ssn_D.getId(), ssn_D);

			final Vector<Integer> aDetections = new Vector<Integer>(0, 1);
			final Vector<Integer> dDetections = new Vector<Integer>(0, 1);

			ssn_A.getRadiatedChars().add(EnvironmentType.BROADBAND_PASSIVE,
					new ASSET.Models.Mediums.BroadbandRadNoise(99));
			ssn_B.getRadiatedChars().add(EnvironmentType.BROADBAND_PASSIVE,
					new ASSET.Models.Mediums.BroadbandRadNoise(99));
			ssn_C.getRadiatedChars().add(EnvironmentType.BROADBAND_PASSIVE,
					new ASSET.Models.Mediums.BroadbandRadNoise(99));
			ssn_D.getRadiatedChars().add(EnvironmentType.BROADBAND_PASSIVE,
					new ASSET.Models.Mediums.BroadbandRadNoise(99));

			ssn_A.addSensor(new BroadbandSensor(12)
			{
				/**
				 * 
				 */
				private static final long serialVersionUID = 1L;

				// what is the detection strength for this target?
				protected DetectionEvent detectThis(EnvironmentType environment,
						ParticipantType host, ParticipantType target, long time,
						ScenarioType scenario)
				{
					aDetections.add(new Integer(target.getId()));
					return null;
				}
			});

			ssn_D.addSensor(new BroadbandSensor(19)
			{
				/**
				 * 
				 */
				private static final long serialVersionUID = 1L;

				// what is the detection strength for this target?
				protected DetectionEvent detectThis(EnvironmentType environment,
						ParticipantType host, ParticipantType target, long time,
						ScenarioType scenario)
				{
					dDetections.add(new Integer(target.getId()));
					return null;
				}
			});

			scen.setScenarioStepTime(1000);

			scen.step();

			// ok, check that the correct participants were informed about what's
			// going on
			assertEquals("we were only told about B", aDetections.size(), 1);
			Integer firstEle = (Integer) aDetections.firstElement();
			assertEquals("we were only told about B", firstEle.intValue(), 13, 0);

			// and for the other track
			assertEquals("we were only told about B", dDetections.size(), 2);
			Integer firstIndex = new Integer(12);
			Integer secondIndex = new Integer(13);

			assertTrue(" we were informed about A", dDetections.contains(firstIndex));
			assertTrue(" we were informed about B", dDetections.contains(secondIndex));

		}

		public void testMonteCarloCreation()
		{
			CoreScenario scen = new CoreScenario();

			SSN ssn_A = new SSN(12);
			SSN ssn_B = new SSN(13);
			SSN ssn_C = new SSN(14);
			SSN ssn_D = new SSN(15);

			Status theStat = new Status(12, 0);
			WorldLocation theLoc = new WorldLocation(1, 1, 1);
			theStat.setSpeed(new WorldSpeed(12, WorldSpeed.Kts));
			theStat.setLocation(theLoc);

			ssn_A.setStatus(theStat);
			ssn_B.setStatus(theStat);
			ssn_C.setStatus(theStat);
			ssn_D.setStatus(theStat);

			// check empty, etc
			assertEquals("first list done", 0, scen._completeParticipantList.size());
			assertEquals("second list done", 0, scen._myInvisibleParticipants.size());
			assertEquals("third list done", 0, scen._myVisibleParticipants.size());

			scen.addParticipant(ssn_A.getId(), ssn_A);

			assertEquals("first list done", 1, scen._completeParticipantList.size());
			assertEquals("second list done", 0, scen._myInvisibleParticipants.size());
			assertEquals("third list done", 1, scen._myVisibleParticipants.size());

			scen.addParticipant(ssn_B.getId(), ssn_B);

			assertEquals("first list done", 2, scen._completeParticipantList.size());
			assertEquals("second list done", 0, scen._myInvisibleParticipants.size());
			assertEquals("third list done", 2, scen._myVisibleParticipants.size());

			scen.addMonteCarloParticipant(ssn_C.getId(), ssn_C);

			assertEquals("first list done", 3, scen._completeParticipantList.size());
			assertEquals("second list done", 1, scen._myInvisibleParticipants.size());
			assertEquals("third list done", 2, scen._myVisibleParticipants.size());

			scen.addMonteCarloParticipant(ssn_D.getId(), ssn_D);

			assertEquals("first list done", 4, scen._completeParticipantList.size());
			assertEquals("second list done", 2, scen._myInvisibleParticipants.size());
			assertEquals("third list done", 2, scen._myVisibleParticipants.size());

			// //////////////////////////////////////////////////////////
			// now try deleting them
			// //////////////////////////////////////////////////////////
			scen.removeParticipant(13);
			assertEquals("first list done", 3, scen._completeParticipantList.size());
			assertEquals("second list done", 2, scen._myInvisibleParticipants.size());
			assertEquals("third list done", 1, scen._myVisibleParticipants.size());

			scen.removeParticipant(15);
			assertEquals("first list done", 2, scen._completeParticipantList.size());
			assertEquals("second list done", 1, scen._myInvisibleParticipants.size());
			assertEquals("third list done", 1, scen._myVisibleParticipants.size());

		}
	}

	public static void main(String[] args)
	{
		ScenarioTest stt = new ScenarioTest("happy");
		stt.testScenarioTimes();

	}

	public Layer getBackdrop()
	{
		return _myBackdrop;
	}

	public void setBackdrop(BaseLayer layer)
	{
		_myBackdrop = layer;
	}

	public void stop()
	{
	}

	/**
	 * store the specified display setting
	 * 
	 * @param key
	 * @param value
	 */
	public void addDisplaySetting(final String key, final String value)
	{
		_displaySettings.put(key, value);
	}

	public String getDisplaySettingFor(final String key)
	{
		return _displaySettings.get(key);
	}
}
