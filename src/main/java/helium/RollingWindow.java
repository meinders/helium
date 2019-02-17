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
 * A rolling window is a FIFO queue of fixed maximum size, with a push operation
 * (called 'add' here) that also pops an element off the queue whenever the
 * maximum size is reached.
 *
 * @author Gerrit Meinders
 */
public class RollingWindow
{
	private int[] window;

	private int offset;

	private int size;

	/**
	 * Constructs a new rolling window of the specified size.
	 *
	 * @param windowSize The size of the window.
	 */
	public RollingWindow( final int windowSize )
	{
		window = new int[ windowSize ];
		offset = 0;
		size = 0;
	}

	/**
	 * Adds a value to the end of the window, replacing the value at the start
	 * of the window. The replaced value is returned. If the window isn't full
	 * yet, the result is always {@code 0}.
	 *
	 * @param value Value to be added.
	 *
	 * @return The value that was previously at the start of the window, which
	 * was removed from the window.
	 */
	public int add( final int value )
	{
		final int removed;

		if ( isFull() )
		{
			removed = window[ offset ];
		}
		else
		{
			removed = 0;
			size++;
		}

		window[ offset++ ] = value;
		offset %= window.length;

		return removed;
	}

	/**
	 * Returns the value at the start of the window. If the window isn't full
	 * yet, the result is {@code 0}.
	 *
	 * @return The value at the start of the window.
	 */
	public int get()
	{
		return ( size == 0 ) ? 0 : window[ offset ];
	}

	/**
	 * Removes the value at the start of the window.
	 *
	 * @return The removed value.
	 */
	public int remove()
	{
		final int removed = window[ offset++ ];
		offset %= window.length;
		size--;
		return removed;
	}

	/**
	 * Returns the number of values currently in the window.
	 *
	 * @return Number of values in the window.
	 */
	public int size()
	{
		return size;
	}

	/**
	 * Returns whether the window is empty.
	 *
	 * @return Whether the window is full.
	 */
	public boolean isEmpty()
	{
		return size == 0;
	}

	/**
	 * Returns whether the window is full. Until the window is full, no values
	 * are removed from it.
	 *
	 * @return Whether the window is full.
	 */
	public boolean isFull()
	{
		return size == window.length;
	}

	/**
	 * Returns the maximum number of values that fit inside the window.
	 *
	 * @return The capacity of the window.
	 */
	public int getCapacity()
	{
		return window.length;
	}

	public int[] getWindow()
	{
		return window;
	}

	public int getOffset()
	{
		return offset;
	}

	public int getSize()
	{
		return size;
	}

	public static class Double
	{
		private double[] window;

		private int offset;

		private int size;

		/**
		 * Constructs a new rolling window of the specified size.
		 *
		 * @param windowSize The size of the window.
		 */
		public Double( final int windowSize )
		{
			window = new double[ windowSize ];
			offset = 0;
			size = 0;
		}

		/**
		 * Adds a value to the end of the window, replacing the value at the
		 * start of the window. The replaced value is returned. If the window
		 * isn't full yet, the result is always {@code 0}.
		 *
		 * @param value Value to be added.
		 *
		 * @return The value that was previously at the start of the window,
		 * which was removed from the window.
		 */
		public double add( final double value )
		{
			final double removed;

			if ( isFull() )
			{
				removed = window[ offset ];
			}
			else
			{
				removed = 0;
				size++;
			}

			window[ offset++ ] = value;
			offset %= window.length;

			return removed;
		}

		/**
		 * Returns the value at the start of the window. If the window isn't
		 * full yet, the result is {@code 0}.
		 *
		 * @return The value at the start of the window.
		 */
		public double get()
		{
			return ( size == 0 ) ? 0 : window[ offset ];
		}

		/**
		 * Removes the value at the start of the window.
		 *
		 * @return The removed value.
		 */
		public double remove()
		{
			final double removed = window[ offset++ ];
			offset %= window.length;
			size--;
			return removed;
		}

		/**
		 * Returns the number of values currently in the window.
		 *
		 * @return Number of values in the window.
		 */
		public int size()
		{
			return size;
		}

		/**
		 * Returns whether the window is empty.
		 *
		 * @return Whether the window is full.
		 */
		public boolean isEmpty()
		{
			return size == 0;
		}

		/**
		 * Returns whether the window is full. Until the window is full, no
		 * values are removed from it.
		 *
		 * @return Whether the window is full.
		 */
		public boolean isFull()
		{
			return size == window.length;
		}

		/**
		 * Returns the maximum number of values that fit inside the window.
		 *
		 * @return The capacity of the window.
		 */
		public int getCapacity()
		{
			return window.length;
		}

		public double[] getWindow()
		{
			return window;
		}

		public int getOffset()
		{
			return offset;
		}

		public int getSize()
		{
			return size;
		}
	}
}
