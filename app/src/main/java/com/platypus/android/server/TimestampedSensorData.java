package com.platypus.android.server;

import com.platypus.crw.data.SensorData;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Created by jason on 10/17/17.
 *
 */

class TimestampedSensorData
{
		private static final Object lock = new Object();
		private static long count = 0;
		private static final long count_cutoff = 1000; // start purging stored values once you hit this
		static HashMap<Long, TimestampedSensorData> allSensorData = new HashMap<>();
		static HashMap<Long, TimestampedSensorData> unsentSensorData = new HashMap<>();

		static TimestampedSensorData getRandomDatum()
		{
				synchronized (lock)
				{
						// pull from the unsent data and send it
						if (unsentSensorData.size() < 1) return null;
						Long[] unsent_ids = unsentSensorData.keySet().toArray(new Long[0]);
						int random_index = ThreadLocalRandom.current().nextInt(0, unsentSensorData.size());
						long index = unsent_ids[random_index];
						return unsentSensorData.get(index);
				}
		}

		static void purgeAll()
		{
				synchronized (lock)
				{
						allSensorData.clear();
						unsentSensorData.clear();
				}
		}

		private static void purgeOldest()
		{
				synchronized (lock)
				{
						long t = System.currentTimeMillis();
						long oldest_id = 0;
						for (Map.Entry<Long, TimestampedSensorData> entry : allSensorData.entrySet())
						{
								long timestamp = entry.getValue().getTimestamp();
								if (timestamp < t)
								{
										t = timestamp;
										oldest_id = entry.getKey();
								}
						}
						if (unsentSensorData.containsKey(oldest_id)) unsentSensorData.remove(oldest_id);
						allSensorData.remove(oldest_id);
				}
		}

		static void acknowledged(long id)
		{
				// TODO: need new core lib function that lets the tablet tell the phone it received this data point
				synchronized (lock)
				{
						allSensorData.get(id).listener_acknowledged = true;
						unsentSensorData.remove(id);
				}
		}

		private SensorData sensorData;
		private long timestamp;
		private long id;
		private boolean listener_acknowledged;

		TimestampedSensorData(SensorData sd)
		{
				sensorData = sd;
				timestamp = System.currentTimeMillis();
				id = count++;
				listener_acknowledged = false;
				synchronized (lock)
				{
						allSensorData.put(id, this);
						unsentSensorData.put(id, this);
				}
				if (allSensorData.size() > count_cutoff)
				{
						purgeOldest();
				}
		}

		public SensorData getSensorData() { return sensorData; }
		public long getId() { return id; }
		private long getTimestamp() { return timestamp; }

}
