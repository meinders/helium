/*
 * Copyright 2013-2019 Gerrit Meinders
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package helium;

/**
 * Implements a rolling maximum function, analogous to a rolling average, to
 * determine the maximum value in a rolling window of fixed size.
 *
 * <p>
 * As opposed to the rolling average, an internal buffer is required matching
 * the given window size. Using this buffer to keep track of local maxima, the
 * algorithm reaches approximately O(n) complexity, by my guess. (While a
 * trivial implementation has O(n^2) complexity.)
 *
 * @author Gerrit Meinders
 */
public class RollingMaximum
{
	/**
	 * Contains all relevant maximum values in descending order, starting at the
	 * index specified by {@link #maximumIndex}.
	 */
	private int[] buffer;

	/**
	 * Index of the lowest value in {@link #buffer}.
	 */
	private int minimumIndex;

	/**
	 * Index of the highest value in {@link #buffer}.
	 */
	private int maximumIndex;

	/**
	 * Constructs a new rolling maximum with the specified window size.
	 *
	 * @param windowSize Window size.
	 */
	public RollingMaximum( final int windowSize )
	{
		buffer = new int[ windowSize ];
		minimumIndex = 0;
		maximumIndex = 0;
	}

	/**
	 * Returns the maximum of the values in the current window.
	 *
	 * @return Maximum value for the current window.
	 */
	public int get()
	{
		return buffer[ maximumIndex ];
	}

	/**
	 * Removes the given value from the rolling maximum. Must be called whenever
	 * a value leaves the rolling window.
	 *
	 * @param value Value to be removed.
	 */
	public void remove( final int value )
	{
		if ( ( value == get() ) && ( maximumIndex != minimumIndex ) )
		{
			maximumIndex++;
			maximumIndex %= buffer.length;
		}
	}

	/**
	 * Adds the given value to the rolling maximum. Must be called whenever a
	 * value enters the rolling window.
	 *
	 * @param value Value to be added.
	 */
	public void add( final int value )
	{
		if ( value > buffer[ minimumIndex ] )
		{
			while ( minimumIndex != maximumIndex )
			{
				final int previousIndex = ( minimumIndex == 0 ) ? buffer.length - 1
				                                                : minimumIndex - 1;
				if ( value <= buffer[ previousIndex ] )
				{
					break;
				}
				minimumIndex = previousIndex;
			}

		}
		else
		{
			minimumIndex++;
			minimumIndex %= buffer.length;

			if ( minimumIndex == maximumIndex )
			{
				throw new IllegalStateException(
					"Added values exceed window size." );
			}
		}

		buffer[ minimumIndex ] = value;
	}

	/**
	 * Returns the window size of the rolling maximum.
	 *
	 * @return Window size of the rolling maximum
	 */
	public int getWindowSize()
	{
		return buffer.length;
	}

	public int[] getBuffer()
	{
		return buffer;
	}

	public int getMinimumIndex()
	{
		return minimumIndex;
	}

	public int getMaximumIndex()
	{
		return maximumIndex;
	}

	public static class Double
	{
		/**
		 * Contains all relevant maximum values in descending order, starting at
		 * the index specified by {@link #maximumIndex}.
		 */
		private double[] buffer;

		/**
		 * Index of the lowest value in {@link #buffer}.
		 */
		private int minimumIndex;

		/**
		 * Index of the highest value in {@link #buffer}.
		 */
		private int maximumIndex;

		/**
		 * Constructs a new rolling maximum with the specified window size.
		 *
		 * @param windowSize Window size.
		 */
		public Double( final int windowSize )
		{
			buffer = new double[ windowSize ];
			minimumIndex = 0;
			maximumIndex = 0;
		}

		/**
		 * Returns the maximum of the values in the current window.
		 *
		 * @return Maximum value for the current window.
		 */
		public double get()
		{
			return buffer[ maximumIndex ];
		}

		/**
		 * Removes the given value from the rolling maximum. Must be called
		 * whenever a value leaves the rolling window.
		 *
		 * @param value Value to be removed.
		 */
		public void remove( final double value )
		{
			if ( ( value == get() ) && ( maximumIndex != minimumIndex ) )
			{
				maximumIndex++;
				maximumIndex %= buffer.length;
			}
		}

		/**
		 * Adds the given value to the rolling maximum. Must be called whenever
		 * a value enters the rolling window.
		 *
		 * @param value Value to be added.
		 */
		public void add( final double value )
		{
			if ( value > buffer[ minimumIndex ] )
			{
				while ( minimumIndex != maximumIndex )
				{
					final int previousIndex = ( minimumIndex == 0 ) ? buffer.length - 1
					                                                : minimumIndex - 1;
					if ( value <= buffer[ previousIndex ] )
					{
						break;
					}
					minimumIndex = previousIndex;
				}

			}
			else
			{
				minimumIndex++;
				minimumIndex %= buffer.length;

				if ( minimumIndex == maximumIndex )
				{
					throw new IllegalStateException(
						"Added values exceed window size." );
				}
			}

			buffer[ minimumIndex ] = value;
		}

		/**
		 * Returns the window size of the rolling maximum.
		 *
		 * @return Window size of the rolling maximum
		 */
		public int getWindowSize()
		{
			return buffer.length;
		}
	}
}
