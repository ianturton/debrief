package com.planetmayo.debrief.satc.model.states;

/**
 * class representing a set of Course bounds
 * 
 * @author ian
 * 
 */
public class CourseRange extends BaseRange
{
	private double _min;
	private double _max;

	public CourseRange(double minCrse, double maxCourse)
	{
		_min = minCrse;
		_max = maxCourse;

		// SPECIAL PROCESSING, FOR COURSES THAT PASS THROUGH ZERO
		if (_min > 180)
		{
			// ok, we're passing through zero - sort it
			_min -= 360;
		}
		if (_max > 180)
		{
			// ok, we're passing through zero - sort it
			_max -= 360;
		}
	}

	/**
	 * copy constructor
	 * 
	 * @param range
	 */
	public CourseRange(CourseRange range)
	{
		this(range.getMin(), range.getMax());
	}

	public double getMin()
	{
		final double res;
		if (_min < 0)
			res = _min + 360;
		else 
			res = _min;
		return res;
	}

	public void setMin(double minCourse)
	{
		_min = minCourse;
	}

	public double getMax()
	{
		final double res;
		if (_max < 0)
			res = _max + 360;
		else 
			res = _max;
		return res;
	}

	public void setMax(double maxCourse)
	{
		_max = maxCourse;
	}

	public void constrainTo(CourseRange sTwo)
	{
		// note: we're using _min and _max because our getter mangles the value to make it human readable
		_min = Math.max(_min, sTwo._min);
		_max = Math.min(_max, sTwo._max);
		
		// aah, but what if we're now impossible?
		if(_max < _min)
			throw new IncompatibleStateException();
	}

}
