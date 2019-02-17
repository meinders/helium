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
 * Allows processing of (uncompressed) audio samples before the data is written
 * to an underlying output stream. Sub-classes may override the
 * {@link #writeSample(int)} method to do whether processing is needed before
 * actually writing the sample.
 *
 * @author Gerrit Meinders
 */
public abstract class FilterAudioOutputStream
	extends FilterOutputStream
{
	/**
	 * Sample format of the stream being normalized.
	 */
	protected final SampleFormat sampleFormat;

	/**
	 * Constructs a new normalizing output stream for data in the specified
	 * audio format.
	 *
	 * @param out    Output stream to write normalized data to.
	 * @param format Audio format of the written data.
	 *
	 * @throws IllegalArgumentException if the specified format isn't supported.
	 */
	public FilterAudioOutputStream( final OutputStream out, final AudioFormat format )
	{
		super( out );

		sampleFormat = getSampleFormat( format );
	}

	@Override
	public final void write( final int b )
		throws IOException
	{
		if ( sampleFormat.update( b ) )
		{
			writeSample( sampleFormat.get() );
		}
	}

	/**
	 * Writes the given sample to the underlying output stream, possibly after
	 * processing it. The sample need not be written immediately, to allow for
	 * internal buffering.
	 *
	 * <p>
	 * The implementation in {@link FilterAudioOutputStream} simply writes the
	 * sample as is.
	 *
	 * @param sample The sample to be written.
	 *
	 * @throws IOException if an I/O error occurs.
	 */
	protected void writeSample( final int sample )
		throws IOException
	{
		sampleFormat.write( out, sample );
	}

	/**
	 * Returns the sample format for the given audio format.
	 *
	 * @param format Audio format to get a sample format for.
	 *
	 * @return Sample format.
	 *
	 * @throws IllegalArgumentException if the specified format isn't supported.
	 */
	private static SampleFormat getSampleFormat( final AudioFormat format )
	{
		if ( format.getEncoding() == AudioFormat.Encoding.PCM_SIGNED )
		{
			switch ( format.getSampleSizeInBits() )
			{
				case 8:
					return new PCM8();
				case 16:
					if ( format.isBigEndian() )
					{
						return new PCM16BE();
					}
					else
					{
						return new PCM16LE();
					}
				default:
					throw new IllegalArgumentException(
						"format: unsupported sample size" );
			}
		}
		else
		{
			throw new IllegalArgumentException( "format: unsupported encoding" );
		}
	}

	/**
	 * Encapsulates input and output operations for audio data in a specific
	 * sample format and provides additional information needed to process this
	 * data once it's decoded.
	 *
	 * @author Gerrit Meinders
	 */
	protected interface SampleFormat
	{
		/**
		 * Returns the current sample. The result is only guaranteed to be a
		 * valid sample if the last call to {@link #update} returned {@code
		 * true}.
		 *
		 * @return Current sample.
		 */
		int get();

		/**
		 * Updates the current sample with the given byte of data. If the method
		 * returns {@code true}, {@link #get} may be called to retrieve the
		 * current sample.
		 *
		 * @param b Byte from the current sample.
		 *
		 * @return Whether this update completed the current sample.
		 */
		boolean update( int b );

		/**
		 * Writes a sample to the given output stream, encoded to this sample
		 * format.
		 *
		 * @param out    Output stream to write to.
		 * @param sample Sample to be written.
		 *
		 * @throws IOException if an I/O error occurs.
		 * @throws IllegalArgumentException if the sample is invalid for this
		 * sample format.
		 */
		void write( OutputStream out, int sample )
			throws IOException;

		/**
		 * Returns the maximum amplitude that can be expressed using this sample
		 * format, which is always a positive value. If the maximum amplitude
		 * differs for positive and negative values, the minimum of the two is
		 * returned.
		 *
		 * @return Maximum amplitude.
		 */
		int getMaximumAmplitude();

		/**
		 * Limits the sample to the maximum amplitude range supported by the
		 * sample format. Any values that exceed the range will be clamped.
		 *
		 * @param sample Sample to be clamped.
		 *
		 * @return Clamped value.
		 */
		int clamp( int sample );
	}

	/**
	 * Abstract base class for uncompressed audio (PCM), 16-bit signed. Two
	 * subclasses define endianness.
	 *
	 * @author Gerrit Meinders
	 */
	private abstract static class PCM16
		implements SampleFormat
	{
		protected short buffer;

		protected boolean complete = true;

		@Override
		public int get()
		{
			return buffer;
		}

		@Override
		public int getMaximumAmplitude()
		{
			return 0x7fff;
		}

		@Override
		public int clamp( final int sample )
		{
			return sample > 0 ? Math.min( 0x7fff, sample ) : Math.max( -0x8000,
			                                                           sample );
		}
	}

	/**
	 * Uncompressed audio (PCM), 16-bit signed, little-endian.
	 *
	 * @author Gerrit Meinders
	 */
	private static class PCM16LE
		extends PCM16
	{
		@Override
		public boolean update( final int b )
		{
			buffer >>= 8;
			buffer &= 0xff;
			buffer |= b << 8;
			return complete = !complete;
		}

		@Override
		public void write( final OutputStream out, final int sample )
			throws IOException
		{
			if ( sample >= 0x8000 || sample < -0x8000 )
			{
				throw new IllegalArgumentException( "sample: " + sample );
			}
			out.write( sample );
			out.write( sample >> 8 );
		}
	}

	/**
	 * Uncompressed audio (PCM), 16-bit signed, big-endian.
	 *
	 * @author Gerrit Meinders
	 */
	private static class PCM16BE
		extends PCM16
	{
		@Override
		public boolean update( final int b )
		{
			buffer <<= 8;
			buffer |= b;
			return complete = !complete;
		}

		@Override
		public void write( final OutputStream out, final int sample )
			throws IOException
		{
			if ( sample >= 0x8000 || sample < -0x8000 )
			{
				throw new IllegalArgumentException( "sample: " + sample );
			}
			out.write( sample >> 8 );
			out.write( sample );
		}
	}

	/**
	 * Uncompressed audio (PCM), 8-bit signed.
	 *
	 * @author Gerrit Meinders
	 */
	private static class PCM8
		implements SampleFormat
	{
		private int buffer;

		@Override
		public int get()
		{
			return buffer;
		}

		@Override
		public boolean update( final int b )
		{
			buffer = b;
			return true;
		}

		@Override
		public void write( final OutputStream out, final int sample )
			throws IOException
		{
			if ( sample >= 0x80 || sample < -0x80 )
			{
				throw new IllegalArgumentException( "sample: " + sample );
			}
			out.write( sample );
		}

		@Override
		public int getMaximumAmplitude()
		{
			return 0x7f;
		}

		@Override
		public int clamp( final int sample )
		{
			return sample > 0 ? Math.min( 0x7f, sample )
			                  : Math.max( -0x80, sample );
		}
	}
}
