/*
 * Copyright 2018-2019 Gerrit Meinders
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

import java.io.*;
import java.util.concurrent.*;

import org.jetbrains.annotations.*;

/**
 * Implements a pipe (as available in UNIX environments) in pure Java code, by
 * transferring data between two streams on an encapsulated thread.
 *
 * @author Gerrit Meinders
 */
public class Pipe
	implements Runnable, Callable<Object>
{
	private final boolean closeOutputStream;

	private InputStream in;

	private OutputStream out;

	private boolean yielding = true;

	/**
	 * Constructs a new pipe between the given streams. The output stream will
	 * be closed when the end of the input stream is reached, or if an error
	 * occurs while reading from the input stream.
	 *
	 * @param in  Stream to read data from.
	 * @param out Stream to write data to.
	 */
	public Pipe( final InputStream in, final OutputStream out )
	{
		this.in = in;
		this.out = out;

		closeOutputStream = true;
	}

	/**
	 * Constructs a new pipe between the given streams. If
	 * <code>closeOutputStream</code> is set, the output stream will be closed
	 * when the end of the input stream is reached, or if an error occurs while
	 * reading from the input stream.
	 *
	 * @param in                Stream to read data from.
	 * @param out               Stream to write data to.
	 * @param closeOutputStream Whether the output stream will be closed when
	 *                          the pipe terminates.
	 */
	public Pipe( final InputStream in, final OutputStream out, final boolean closeOutputStream )
	{
		this.in = in;
		this.out = out;
		this.closeOutputStream = closeOutputStream;
	}

	@Override
	public void run()
	{
		try
		{
			call();
		}
		catch ( final IOException e )
		{
			e.printStackTrace();
		}
	}

	@Nullable
	@Override
	public Object call()
		throws IOException
	{
		System.out.println( "Pipe running..." );
		try
		{
			int read;
			final byte[] buffer = new byte[ 0x1000 ];
			while ( ( read = in.read( buffer ) ) != -1 )
			{
				out.write( buffer, 0, read );
				if ( yielding )
				{
					Thread.yield();
				}
			}
		}
		finally
		{
			if ( closeOutputStream )
			{
				System.out.println( "Output stream closed." );
				out.close();
			}
		}
		return null;
	}
}
