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

import java.io.*;

/**
 * Encodes an audio stream using LAME and writes the result to the underlying
 * output stream.
 *
 * <p>
 * To encode the audio stream, LAME is run as a seperate process and accessed
 * through that process' standard input and output streams. This approach allows
 * use of LAME without any platform-specific code and without legal issues.
 *
 * <p>
 * This implementation was written for LAME 3.96, but probably works with other
 * versions as well.
 *
 * @author Gerrit Meinders
 */
public class LameOutputStream
	extends FilterOutputStream
{
	private OutputStream encoderInput;

	private Thread encoderOutputThread;

	/**
	 * Constructs a new output stream for encoding audio with LAME.
	 *
	 * @param out     Output stream to write encoded data to.
	 * @param options Specifies encoding options.
	 *
	 * @throws IOException if an I/O error occurs.
	 */
	public LameOutputStream( final OutputStream out, final Mp3Options options )
		throws IOException
	{
		super( out );

		if ( options == null )
		{
			throw new NullPointerException( "options" );
		}

		final Process encoder = new ProcessBuilder( options.getCommand() ).start();

		encoderInput = encoder.getOutputStream();

		final Pipe encoderOutput = new Pipe( encoder.getInputStream(), out );
		final Pipe encoderError = new Pipe( encoder.getErrorStream(), System.err );

		encoderOutputThread = new Thread( encoderOutput );
		encoderOutputThread.start();

		new Thread( encoderError ).start();
	}

	@Override
	public void write( final int b )
		throws IOException
	{
		encoderInput.write( b );
	}

	@Override
	public void write( final byte[] b, final int off, final int len )
		throws IOException
	{
		encoderInput.write( b, off, len );
	}

	@Override
	public void flush()
		throws IOException
	{
		encoderInput.flush();
		super.flush();
	}

	@Override
	public void close()
		throws IOException
	{
		encoderInput.close();
		try
		{
			encoderOutputThread.join();
		}
		catch ( final InterruptedException e )
		{
			throw new IOException( e );
		}
		super.close();
	}

}
