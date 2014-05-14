package com.planetmayo.debrief.satc.model.legs;

import java.util.List;

import com.planetmayo.debrief.satc.model.states.BoundedState;
import com.planetmayo.debrief.satc.model.states.SpeedRange;
import com.vividsolutions.jts.geom.Point;

public class AlteringLeg extends CoreLeg
{

	public AlteringLeg(String name)
	{
		super(name);
	}
	
	/**
	 * create an altering leg.
	 * 
	 * @param name
	 *          what to call the leg
	 * @param states
	 *          the set of bounded states that comprise the leg
	 */
	public AlteringLeg(String name, List<BoundedState> states)
	{
		super(name, states);
	}	

	/**
	 * use a simple speed/time decision to decide if it's possible to navigate a
	 * route
	 */
	public void decideAchievableRoute(CoreRoute r)
	{
		if (! (r instanceof AlteringRoute)) {
			return;
		}
		AlteringRoute route = (AlteringRoute) r; 
		SpeedRange speedRange = _states.get(0).getSpeed();
		double distance = route.getDirectDistance();
		double elapsed = route.getElapsedTime();
		double speed = distance / elapsed;

		if (!speedRange.allows(speed))
		{
			route.setImpossible();
		}		
	}

	@Override
	public LegType getType()
	{
		return LegType.ALTERING;
	}

	@Override
	public CoreRoute createRoute(Point start, Point end, String name)
	{
		name = name == null ? getName() : name;
		AlteringRoute route = new AlteringRoute(name, start, getFirst().getTime(), 
				end, getLast().getTime());
		route.generateSegments(_states);
		return route;
	}
}