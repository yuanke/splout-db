package com.splout.db.common;

/*
 * #%L
 * Splout SQL commons
 * %%
 * Copyright (C) 2012 Datasalt Systems S.L.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.splout.db.common.JSONSerDe;
import com.splout.db.common.PartitionEntry;
import com.splout.db.common.PartitionMap;
import com.splout.db.common.JSONSerDe.JSONSerDeException;

public class TestPartitionMap {
	
	protected PartitionMap testPartitionMap() {
		List<PartitionEntry> entries = new ArrayList<PartitionEntry>();
		PartitionEntry entry = new PartitionEntry();
		entry.setMin("a");
		entry.setMax("c");
		entry.setShard(0);
		entries.add(entry);
		
		entry = new PartitionEntry();
		entry.setMin("c");
		entry.setMax("f");
		entry.setShard(1);
		entries.add(entry);
		
		entry = new PartitionEntry();
		entry.setMin("f");
		entry.setMax("m");
		entry.setShard(2);
		entries.add(entry);
		
		entry = new PartitionEntry();
		entry.setMin("m");
		entry.setMax("x");
		entry.setShard(3);
		entries.add(entry);
		
		entry = new PartitionEntry();
		entry.setMin("x");
		entry.setMax(null);
		entry.setShard(4);
		entries.add(entry);

		PartitionMap map = new PartitionMap();
		map.setPartitionEntries(entries);
		return map;
	}
	
	@Test
	public void testAdjustEmptyPartitions() {
		List<PartitionEntry> entries = new ArrayList<PartitionEntry>();
		PartitionEntry entry = new PartitionEntry();
		entry.setMin(null);
		entry.setMax("c");
		entry.setShard(0);
		entries.add(entry);
		
		// Partition entry that would become empty - has to be removed
		// min = max
		entry = new PartitionEntry();
		entry.setMin("c");
		entry.setMax("c");
		entry.setShard(1);
		entries.add(entry);
		
		entry = new PartitionEntry();
		entry.setMin("c");
		entry.setMax("m");
		entry.setShard(2);
		entries.add(entry);
		
		// Partition entry that would become empty - has to be removed
		// min = max
		entry = new PartitionEntry();
		entry.setMin("m");
		entry.setMax("m");
		entry.setShard(3);
		entries.add(entry);
		
		entry = new PartitionEntry();
		entry.setMin("m");
		entry.setMax(null);
		entry.setShard(4);
		entries.add(entry);

		List<PartitionEntry> newEntries = PartitionMap.adjustEmptyPartitions(entries);
		assertEquals(3, newEntries.size());
		
		assertEquals(null, newEntries.get(0).getMin());
		assertEquals("c", newEntries.get(0).getMax());
		assertEquals((Integer)0, newEntries.get(0).getShard());
		
		assertEquals("c", newEntries.get(1).getMin());
		assertEquals("m", newEntries.get(1).getMax());
		assertEquals((Integer)1, newEntries.get(1).getShard());

		assertEquals("m", newEntries.get(2).getMin());
		assertEquals(null, newEntries.get(2).getMax());
		assertEquals((Integer)2, newEntries.get(2).getShard());
	}
	
  @Test
	public void testOpenedMapSerDe() throws Exception {
		PartitionMap map = PartitionMap.oneShardOpenedMap();
		String json = JSONSerDe.ser(map);
		map = (PartitionMap) JSONSerDe.deSer(json, PartitionMap.PARTITION_MAP_REF);
		assertEquals((Integer)0, map.getPartitionEntries().get(0).getShard());
	}
	
  /*
   * Because keys are always strings,
   * For using a kind of numeric comparison one needs to use padding.
   * You can use padding easily with method String.format(),
   */
  @Test
  public void testNumericComparison() {
		List<PartitionEntry> entries = new ArrayList<PartitionEntry>();

		for(int i = 0; i < 20; i++) {
  		int min = i * 50;
  		int max = (i+1) * 50;
  		PartitionEntry entry = new PartitionEntry();
  		entry.setMin(String.format("%05d", min));
  		entry.setMax(String.format("%05d", max));
  		entry.setShard(i);
  		entries.add(entry);
  	}
		
		entries.get(0).setMin(null);
		entries.get(19).setMax(null);
		
		PartitionMap partitionMap = new PartitionMap(entries);
		
		assertEquals(100 / 50, partitionMap.findPartition(String.format("%05d", 100)));
		assertEquals(200 / 50, partitionMap.findPartition(String.format("%05d", 200)));
		assertEquals(300 / 50, partitionMap.findPartition(String.format("%05d", 300)));
  }
  
	@Test
	public void testNormalQuery() {
		PartitionMap map = testPartitionMap();
		assertEquals(0, map.findPartition("a"));
		assertEquals(0, map.findPartition("b"));
		assertEquals(0, map.findPartition("aa"));
		assertEquals(1, map.findPartition("d"));
		assertEquals(2, map.findPartition("g"));
		assertEquals(3, map.findPartition("n"));
		assertEquals(4, map.findPartition("x"));
		assertEquals(4, map.findPartition("xxxxxxxxxxx"));
		assertEquals(PartitionMap.NO_PARTITION, map.findPartition("5"));
		
		map.getPartitionEntries().get(0).setMin(null); // open to -Infinity
		assertEquals(0, map.findPartition("5"));

	}

	@Test
	public void testRangeQuery() {
		PartitionMap map = testPartitionMap();
		List<Integer> partitions = map.findPartitions("c", "n");
		assertEquals(3, partitions.size());
		assertTrue(partitions.contains(1));
		assertTrue(partitions.contains(2));
		assertTrue(partitions.contains(3));
		
		partitions = map.findPartitions("a", "n");
		assertEquals(4, partitions.size());
		assertTrue(partitions.contains(0));
		assertTrue(partitions.contains(1));
		assertTrue(partitions.contains(2));
		assertTrue(partitions.contains(3));
				
		partitions = map.findPartitions("m", "xx");
		assertEquals(2, partitions.size());
		assertTrue(partitions.contains(3));
		assertTrue(partitions.contains(4));

		partitions = map.findPartitions("x", "xxxx");
		assertEquals(1, partitions.size());
		assertTrue(partitions.contains(4));
	}
	
	@Test
	public void test() throws JSONSerDeException {
		List<PartitionEntry> entries = new ArrayList<PartitionEntry>();
		PartitionEntry entry = new PartitionEntry();
		entry.setMax("a");
		entry.setMin("b");
		entry.setShard(0);
		entries.add(entry);
		PartitionMap map = new PartitionMap();
		map.setPartitionEntries(entries);
		
		String json = JSONSerDe.ser(map);
		PartitionMap map2 = JSONSerDe.deSer(json, PartitionMap.PARTITION_MAP_REF);
		assertEquals(map2.getPartitionEntries().toString(), map.getPartitionEntries().toString());
	}
}
