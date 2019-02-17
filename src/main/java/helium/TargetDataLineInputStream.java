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
import javax.sound.sampled.*;

/**
 * Input stream that reads raw data from a {@link TargetDataLine}.
 *
 * @author Gerrit Meinders
 */
public class TargetDataLineInputStream
	extends InputStream
{
	private final TargetDataLine targetDataLine;

	private final byte[] buffer;

	private final Object startLock = new Object();

	private int bufferOffset;

	private int bufferDataLength;

	private volatile boolean started = false;

	TargetDataLineInputStream( final TargetDataLine targetDataLine,
	                           final AudioFormat audioFormat )
		throws LineUnavailableException
	{
		this.targetDataLine = targetDataLine;

		targetDataLine.open( audioFormat );
		final int frameSize = audioFormat.getFrameSize();
		buffer = new byte[ ( ( targetDataLine.getBufferSize() / 8 + frameSize - 1 ) / frameSize )
		                   * frameSize ];
	}

	public void start()
	{
		synchronized ( startLock )
		{
			if ( !started )
			{
				targetDataLine.start();
				started = true;
				startLock.notifyAll();
			}
		}
	}

	public boolean isStarted()
	{
		return started;
	}

	public void stop()
	{
		synchronized ( startLock )
		{
			if ( started )
			{
				targetDataLine.stop();
				started = false;
			}
		}
	}

	public void drain()
	{
		targetDataLine.drain();
	}

	@Override
	public int read()
	{
		while ( targetDataLine.isOpen() && ( bufferOffset == bufferDataLength ) )
		{
			bufferDataLength = targetDataLine.read( buffer, 0, buffer.length );
			bufferOffset = 0;
			if ( targetDataLine.isOpen() && ( bufferOffset == bufferDataLength ) )
			{
				try
				{
					Thread.sleep( 100 );
				}
				catch ( final InterruptedException e )
				{
					break;
				}
			}
		}

		final boolean eof = ( bufferOffset == bufferDataLength );

		final int result;
		if ( eof )
		{
			result = -1;
			System.out.println( "Recording stream closed" );
		}
		else
		{
			result = buffer[ bufferOffset++ ] & 0xff;
		}

		return result;
	}

	@Override
	public void close()
	{
		targetDataLine.close();
	}
}
