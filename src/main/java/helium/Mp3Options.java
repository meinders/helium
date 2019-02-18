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
import java.util.*;

import org.jetbrains.annotations.*;
import org.json.*;

/**
 * Allows command-line options for LAME to be specified.
 *
 * @author Gerrit Meinders
 */
public class Mp3Options
{
	@NotNull
	private File executable = new File( "lame.exe" );

	private Mode mode = null;

	private BitRate bitRate = null;

	@NotNull
	public File getExecutable()
	{
		return executable;
	}

	public void setExecutable( @NotNull final File executable )
	{
		this.executable = executable;
	}

	public Mode getMode()
	{
		return mode;
	}

	public void setMode( final Mode mode )
	{
		this.mode = mode;
	}

	public BitRate getBitRate()
	{
		return bitRate;
	}

	public void setBitRate( final BitRate bitRate )
	{
		this.bitRate = bitRate;
	}

	public List<String> getCommand()
	{
		if ( !executable.canExecute() )
		{
			throw new IllegalArgumentException( "executable" );
		}

		final List<String> command = new ArrayList<>();
		command.add( executable.getPath() );

		if ( mode != null )
		{
			switch ( mode )
			{
				case STEREO:
					command.add( "-ms" );
					break;
				case JOINT_STEREO:
					command.add( "-mj" );
					break;
				case MONO:
					command.add( "-mm" );
					break;
			}
		}

		if ( bitRate != null )
		{
			bitRate.apply( command );
		}

		command.add( "--ty" ); // audio/song year of issue (1 to 9999)
		final Calendar calendar = Calendar.getInstance();
		command.add( String.valueOf( calendar.get( Calendar.YEAR ) ) );
		command.add( "--add-id3v2" );// force addition of version 2 tag
		command.add( "--pad-id3v2" );// pad version 2 tag with extra 128 bytes

		command.add( "--quiet" );
		command.add( "-" ); // standard input/output

		return command;
	}

	public enum Mode
	{
		STEREO, JOINT_STEREO, MONO
	}

	public abstract static class BitRate
	{
		protected BitRate()
		{
		}

		abstract void apply( Collection<String> command );

		public abstract JSONObject save();
	}

	public static final class ConstantBitRate
		extends BitRate
	{
		private Integer bitRate;

		public ConstantBitRate( final Integer bitRate )
		{
			this.bitRate = bitRate;
		}

		public Integer getBitRate()
		{
			return bitRate;
		}

		@Override
		void apply( final Collection<String> command )
		{
			if ( bitRate == null )
			{
				command.add( "--cbr" );
			}
			else
			{
				command.add( "-b" + bitRate );
			}
		}

		@Override
		public JSONObject save()
		{
			return new JSONObject().put( "constant", JSONObject.wrap( bitRate ) );
		}
	}

	public static final class AverageBitRate
		extends BitRate
	{
		private int bitRate;

		public AverageBitRate( final int bitRate )
		{
			this.bitRate = bitRate;
		}

		public int getBitRate()
		{
			return bitRate;
		}

		@Override
		void apply( final Collection<String> command )
		{
			command.add( "--abr" );
			command.add( String.valueOf( bitRate ) );
		}

		@Override
		public JSONObject save()
		{
			return new JSONObject().put( "average", bitRate );
		}
	}

	public static final class VariableBitRate
		extends BitRate
	{
		private Integer quality;

		public VariableBitRate( final Integer quality )
		{
			this.quality = quality;
		}

		public Integer getQuality()
		{
			return quality;
		}

		@Override
		void apply( final Collection<String> command )
		{
			command.add( "--vbr-new" );
			if ( quality != null )
			{
				command.add( "-V" + quality );
			}
		}

		@Override
		public JSONObject save()
		{
			return new JSONObject().put( "variable", JSONObject.wrap( quality ) );
		}
	}
}
