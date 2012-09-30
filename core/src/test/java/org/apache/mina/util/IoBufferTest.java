package org.apache.mina.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import org.junit.Test;

public class IoBufferTest {
    /**
     * Test the addition of 3 heap buffers with data
     */
    @Test
    public void testAddHeapBuffers() {
        ByteBuffer bb1 = ByteBuffer.allocate(5);
        bb1.put("012".getBytes());
        bb1.flip();

        ByteBuffer bb2 = ByteBuffer.allocate(5);
        bb2.put("345".getBytes());
        bb2.flip();

        ByteBuffer bb3 = ByteBuffer.allocate(5);
        bb3.put("6789".getBytes());
        bb3.flip();

        IoBuffer ioBuffer = new IoBuffer();
        ioBuffer.add(bb1, bb2).add(bb3);

        assertEquals(0, ioBuffer.position());
        assertEquals(10, ioBuffer.limit());
        assertEquals(10, ioBuffer.capacity());
        assertTrue(ioBuffer.hasRemaining());

        for (int i = 0; i < 10; i++) {
            assertTrue(ioBuffer.hasRemaining());
            assertEquals("0123456789".charAt(i), ioBuffer.get());
        }

        try {
            assertFalse(ioBuffer.hasRemaining());
            ioBuffer.get();
            fail();
        } catch (BufferUnderflowException bufe) {
            assertTrue(true);
        }
    }

    /**
     * Test the addition of 3 heap buffers, one being empty
     */
    @Test
    public void testAddHeapBuffersOneEmpty() {
        ByteBuffer bb1 = ByteBuffer.allocate(5);
        bb1.put("012".getBytes());
        bb1.flip();

        ByteBuffer bb2 = ByteBuffer.allocate(0);

        ByteBuffer bb3 = ByteBuffer.allocate(5);
        bb3.put("3456".getBytes());
        bb3.flip();

        IoBuffer ioBuffer = new IoBuffer();
        ioBuffer.add(bb1, bb2).add(bb3);

        assertEquals(0, ioBuffer.position());
        assertEquals(7, ioBuffer.limit());
        assertEquals(7, ioBuffer.capacity());

        for (int i = 0; i < 7; i++) {
            assertTrue(ioBuffer.hasRemaining());
            assertEquals("0123456".charAt(i), ioBuffer.get());
        }

        try {
            ioBuffer.get();
            fail();
        } catch (BufferUnderflowException bufe) {
            assertTrue(true);
        }
    }

    /**
     * Test the addition of mixed type buffers
     */
    @Test(expected = RuntimeException.class)
    public void testAddMixedTypeBuffers() {
        ByteBuffer bb1 = ByteBuffer.allocate(5);
        bb1.put("012".getBytes());
        bb1.flip();

        ByteBuffer bb2 = ByteBuffer.allocateDirect(5);
        bb2.put("3456".getBytes());
        bb2.flip();

        IoBuffer ioBuffer = new IoBuffer();
        ioBuffer.add(bb1, bb2);
    }

    /**
     * Test the addition of mixed order buffers
     */
    @Test(expected = RuntimeException.class)
    public void testAddMixedOrderBuffers() {
        ByteBuffer bb1 = ByteBuffer.allocate(5);
        bb1.order(ByteOrder.LITTLE_ENDIAN);
        bb1.put("012".getBytes());
        bb1.flip();

        ByteBuffer bb2 = ByteBuffer.allocateDirect(5);
        bb1.order(ByteOrder.BIG_ENDIAN);
        bb2.put("3456".getBytes());
        bb2.flip();

        IoBuffer ioBuffer = new IoBuffer();
        ioBuffer.add(bb1, bb2);
    }

    //-------------------------------------------------------------------------
    // Test the allocate(int) method
    // 1) allocation with a negative value
    // 2) allocation with a 0 length
    // 3) allocation with a 1024 value
    //-------------------------------------------------------------------------
    /**
     * Test the allocation of a new heap IoBuffer with a negative value
     */
    @Test(expected = IllegalArgumentException.class)
    public void testAllocateNegative() {
        IoBuffer.allocate(-1);
    }

    /**
     * Test the allocation of a new heap IoBuffer with no byte in it
     */
    @Test
    public void testAllocate0() {
        IoBuffer ioBuffer = IoBuffer.allocate(0);

        assertFalse(ioBuffer.isDirect());
        assertEquals(0, ioBuffer.capacity());
        assertEquals(0, ioBuffer.limit());
        assertEquals(0, ioBuffer.position());
        assertFalse(ioBuffer.hasRemaining());
    }

    /**
     * Test the allocation of a new heap IoBuffer with 1024 bytes
     */
    @Test
    public void testAllocate1024() {
        IoBuffer ioBuffer = IoBuffer.allocate(1024);

        assertFalse(ioBuffer.isDirect());
        assertEquals(1024, ioBuffer.capacity());
        assertEquals(1024, ioBuffer.limit());
        assertEquals(0, ioBuffer.position());
        assertTrue(ioBuffer.hasRemaining());
    }

    //-------------------------------------------------------------------------
    // Test the allocateDirect(int) method. We check :
    // 1) allocation with a negative value
    // 2) allocation with a 0 length
    // 3) allocation with a 1024 value
    //-------------------------------------------------------------------------
    /**
     * Test the allocation of a new heap IoBuffer with a negative value
     */
    @Test(expected = IllegalArgumentException.class)
    public void testAllocateDirectNegative() {
        IoBuffer.allocate(-1);
    }

    /**
     * Test the allocation of a new direct IoBuffer with no byte in it
     */
    @Test
    public void testAllocateDirect0() {
        IoBuffer ioBuffer = IoBuffer.allocateDirect(0);

        assertTrue(ioBuffer.isDirect());
        assertEquals(0, ioBuffer.capacity());
        assertEquals(0, ioBuffer.limit());
        assertEquals(0, ioBuffer.position());
        assertFalse(ioBuffer.hasRemaining());
    }

    /**
     * Test the allocation of a new direct IoBuffer with 1024 bytes
     */
    @Test
    public void testAllocateDirect1024() {
        IoBuffer ioBuffer = IoBuffer.allocateDirect(1024);

        assertTrue(ioBuffer.isDirect());
        assertEquals(1024, ioBuffer.capacity());
        assertEquals(1024, ioBuffer.limit());
        assertEquals(0, ioBuffer.position());
        assertTrue(ioBuffer.hasRemaining());
    }

    /**
     * Test the get() method on a IoBuffer containing one ByteBuffer with 3 bytes
     */
    @Test
    public void testGetOneBuffer3Bytes() {
        ByteBuffer bb = ByteBuffer.allocate(5);
        bb.put("012".getBytes());
        bb.flip();

        IoBuffer ioBuffer = new IoBuffer(bb);
        assertEquals(0, ioBuffer.position());
        assertEquals(3, ioBuffer.limit());

        assertTrue(ioBuffer.hasRemaining());
        assertEquals('0', ioBuffer.get());
        assertTrue(ioBuffer.hasRemaining());
        assertEquals('1', ioBuffer.get());
        assertTrue(ioBuffer.hasRemaining());
        assertEquals('2', ioBuffer.get());

        try {
            assertFalse(ioBuffer.hasRemaining());
            ioBuffer.get();
            fail();
        } catch (BufferUnderflowException bufe) {
            // expected
            assertEquals(3, ioBuffer.position());
        }
    }

    /**
     * Test the get() method on a IoBuffer containing one ByteBuffer with 0 bytes
     */
    @Test
    public void testGetOneBuffer0Bytes() {
        ByteBuffer bb = ByteBuffer.allocate(0);

        IoBuffer ioBuffer = new IoBuffer(bb);
        assertEquals(0, ioBuffer.position());
        assertEquals(0, ioBuffer.limit());

        try {
            assertFalse(ioBuffer.hasRemaining());
            ioBuffer.get();
            fail();
        } catch (BufferUnderflowException bufe) {
            // expected
            assertEquals(0, ioBuffer.position());
        }
    }

    /**
     * Test the get() method on a IoBuffer containing two ByteBuffer with 3 bytes
     */
    @Test
    public void testGetTwoBuffer3Bytes() {
        ByteBuffer bb1 = ByteBuffer.allocate(5);
        bb1.put("012".getBytes());
        bb1.flip();

        ByteBuffer bb2 = ByteBuffer.allocate(5);
        bb2.put("345".getBytes());
        bb2.flip();

        IoBuffer ioBuffer = new IoBuffer(bb1, bb2);

        assertEquals(0, ioBuffer.position());
        assertEquals(6, ioBuffer.limit());
        assertTrue(ioBuffer.hasRemaining());

        assertEquals('0', ioBuffer.get());
        assertTrue(ioBuffer.hasRemaining());
        assertEquals('1', ioBuffer.get());
        assertTrue(ioBuffer.hasRemaining());
        assertEquals('2', ioBuffer.get());
        assertTrue(ioBuffer.hasRemaining());
        assertEquals('3', ioBuffer.get());
        assertTrue(ioBuffer.hasRemaining());
        assertEquals('4', ioBuffer.get());
        assertTrue(ioBuffer.hasRemaining());
        assertEquals('5', ioBuffer.get());

        try {
            assertFalse(ioBuffer.hasRemaining());
            ioBuffer.get();
            fail();
        } catch (BufferUnderflowException bufe) {
            // expected
            assertEquals(6, ioBuffer.position());
        }
    }

    //-------------------------------------------------------------------------
    // Test the array() method. We will check those cases :
    // 1) array over an empty buffer: we should get an empty byte array
    // 2) array over a buffer with one single empty ByteBuffer 
    // 3) array over a buffer with one single ByteBuffer with data
    // 4) array over a buffer containing many ByteBuffers 
    //-------------------------------------------------------------------------
    /**
     * Test the array method for a IoBuffer containing one empty ByteBuffer
     */
    @Test
    public void testArrayEmptyByteBuffer() {
        IoBuffer ioBuffer = new IoBuffer();

        byte[] array = ioBuffer.array();
        assertNotNull(array);
        assertEquals(0, array.length);
        assertTrue(Arrays.equals(new byte[] {}, array));
    }

    /**
     * Test the array method for a IoBuffer containing one ByteBuffer (cases 2 and 3)
     */
    @Test
    public void testArrayOneByteBuffer() {
        ByteBuffer bb1 = ByteBuffer.allocate(5);
        IoBuffer ioBuffer = new IoBuffer(bb1);

        // Empty buffer first
        byte[] array = ioBuffer.array();
        assertNotNull(array);
        assertEquals(5, array.length);
        assertTrue(Arrays.equals(new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00 }, array));

        // Buffer with data
        bb1.put("012".getBytes());
        bb1.flip();

        ioBuffer = new IoBuffer(bb1);

        array = ioBuffer.array();
        assertNotNull(array);
        assertEquals(3, array.length);
        assertTrue(Arrays.equals(new byte[] { '0', '1', '2' }, array));
    }

    /**
     * Test the array method for a IoBuffer containing one ByteBuffer not initialized
     */
    @Test
    public void testArrayByteBufferNotInitialized() {
        ByteBuffer bb = ByteBuffer.allocate(3);
        IoBuffer ioBuffer = new IoBuffer(bb);

        byte[] array = ioBuffer.array();
        assertNotNull(array);
        assertEquals(3, array.length);
        assertTrue(Arrays.equals(new byte[] { 0x00, 0x00, 0x00 }, array));
    }

    /**
     * Test the array method for a IoBuffer containing three ByteBuffers
     */
    @Test
    public void testArrayThreeByteBuffers() {
        ByteBuffer bb1 = ByteBuffer.allocate(5);
        bb1.put("012".getBytes());
        bb1.flip();

        ByteBuffer bb2 = ByteBuffer.allocate(0);

        ByteBuffer bb3 = ByteBuffer.allocate(5);
        bb3.put("3456".getBytes());
        bb3.flip();

        IoBuffer ioBuffer = new IoBuffer(bb1, bb2, bb3);

        byte[] array = ioBuffer.array();
        assertNotNull(array);
        assertEquals(7, array.length);
        assertTrue(Arrays.equals(new byte[] { '0', '1', '2', '3', '4', '5', '6' }, array));
    }

    /**
     * Test the getInt() method, on a buffer containing 2 ints in one ByteBuffer
     */
    @Test
    public void testGetInt2IntsOneBB() {
        ByteBuffer bb = ByteBuffer.allocate(8);
        bb.putInt(12345);
        bb.putInt(67890);
        bb.flip();

        IoBuffer ioBuffer = new IoBuffer(bb);
        assertEquals(12345, ioBuffer.getInt());
        assertEquals(67890, ioBuffer.getInt());
    }

    /**
     * Test the getInt() method, on a buffer containing 2 ints in two ByteBuffers
     */
    @Test
    public void testGetInt2Ints2BBs() {
        ByteBuffer bb1 = ByteBuffer.allocate(4);
        bb1.putInt(12345);
        bb1.flip();

        ByteBuffer bb2 = ByteBuffer.allocate(4);
        bb2.putInt(67890);
        bb2.flip();

        IoBuffer ioBuffer = new IoBuffer(bb1, bb2);

        assertEquals(12345, ioBuffer.getInt());
        assertEquals(67890, ioBuffer.getInt());
    }

    /**
     * Test the getInt() method, on a buffer containing 2 ints in two ByteBuffers
     * with LittleInidan order
     */
    @Test
    public void testGetInt2Ints2BBsLittleIndian() {
        ByteBuffer bb1 = ByteBuffer.allocate(4);
        bb1.order(ByteOrder.LITTLE_ENDIAN);
        bb1.putInt(12345);
        bb1.flip();

        ByteBuffer bb2 = ByteBuffer.allocate(4);
        bb2.order(ByteOrder.LITTLE_ENDIAN);
        bb2.putInt(67890);
        bb2.flip();

        IoBuffer ioBuffer = new IoBuffer(bb1, bb2);

        assertEquals(12345, ioBuffer.getInt());
        assertEquals(67890, ioBuffer.getInt());
    }

    /**
     * Test the getInt() method, on a buffer containing 1 int spread in two ByteBuffers
     */
    @Test
    public void testGetInt1Int2BBs() {
        ByteBuffer bb1 = ByteBuffer.allocate(1);
        bb1.put((byte) 0x01);
        bb1.flip();

        ByteBuffer bb2 = ByteBuffer.allocate(3);
        bb2.put(new byte[] { 0x02, 0x03, 0x04 });
        bb2.flip();

        IoBuffer ioBuffer = new IoBuffer(bb1, bb2);

        assertEquals(0x01020304, ioBuffer.getInt());
    }

    /**
     * Test the getInt() method, on a buffer containing 1 incomplet int spread in two ByteBuffers
     */
    @Test(expected = BufferUnderflowException.class)
    public void testGetIntIncompletInt2BBs() {
        ByteBuffer bb1 = ByteBuffer.allocate(1);
        bb1.put((byte) 0x01);
        bb1.flip();

        ByteBuffer bb2 = ByteBuffer.allocate(2);
        bb2.put(new byte[] { 0x02, 0x03 });
        bb2.flip();

        IoBuffer ioBuffer = new IoBuffer(bb1, bb2);

        ioBuffer.getInt();
    }

    /**
     * test the get(int) method on one buffer
     */
    @Test
    public void testGetIntOneBuffer() {
        ByteBuffer bb = ByteBuffer.allocate(4);
        bb.put("0123".getBytes());
        bb.flip();

        IoBuffer ioBuffer = new IoBuffer(bb);

        assertEquals('0', ioBuffer.get());
        assertEquals('1', ioBuffer.get());
        assertEquals('0', ioBuffer.get(0));
        assertEquals('3', ioBuffer.get(3));
        assertEquals('1', ioBuffer.get(1));
        assertEquals('2', ioBuffer.get(2));
        assertEquals('2', ioBuffer.get());

        try {
            ioBuffer.get(4);
            fail();
        } catch (IndexOutOfBoundsException ioobe) {
            // expected
            assertTrue(true);
        }
    }

    /**
     * test the get(int) method on two buffers
     */
    @Test
    public void testGetIntTwoBuffer() {
        ByteBuffer bb1 = ByteBuffer.allocate(4);
        bb1.put("0123".getBytes());
        bb1.flip();

        ByteBuffer bb2 = ByteBuffer.allocate(4);
        bb2.put("4567".getBytes());
        bb2.flip();

        IoBuffer ioBuffer = new IoBuffer(bb1, bb2);

        assertEquals('0', ioBuffer.get());
        assertEquals('1', ioBuffer.get());
        assertEquals('0', ioBuffer.get(0));
        assertEquals('4', ioBuffer.get(4));
        assertEquals('7', ioBuffer.get(7));
        assertEquals('2', ioBuffer.get(2));
        assertEquals('2', ioBuffer.get());
        assertEquals('3', ioBuffer.get());
        assertEquals('4', ioBuffer.get());

        try {
            ioBuffer.get(8);
            fail();
        } catch (IndexOutOfBoundsException ioobe) {
            // expected
            assertTrue(true);
        }
    }

    //-------------------------------------------------------------------------
    // The the clear method. It will erase all the data in all the inner
    // ByteBuffer, thus the buffer size might increase.
    // We will check those case :
    // 1) clear an empty buffer
    // 2) clear a buffer with one ByteBuffer
    // 3) clear a buffer with numerous ByteBuffers
    //-------------------------------------------------------------------------
    /**
     * Test the clear() method
     */
    @Test
    public void testClearEmptyBuffer() {
        ByteBuffer bb1 = ByteBuffer.allocate(4);
        bb1.put("012".getBytes());
        bb1.flip();

        ByteBuffer bb2 = ByteBuffer.allocate(4);
        bb2.put("345".getBytes());
        bb2.flip();

        IoBuffer ioBuffer = new IoBuffer(bb1, bb2);

        assertEquals(6, ioBuffer.limit());

        // Move forward a bit
        ioBuffer.get();
        ioBuffer.get();

        // Clear
        ioBuffer.clear();

        // We should be back to the origin
        assertEquals(0, ioBuffer.position());

        // The limit must have grown
        assertEquals(8, ioBuffer.limit());
    }

    /**
     * Test the flip() method
     */
    @Test
    public void testFlip() {
        ByteBuffer bb1 = ByteBuffer.allocate(4);
        bb1.put("0123".getBytes());
        bb1.flip();

        ByteBuffer bb2 = ByteBuffer.allocate(4);
        bb2.put("4567".getBytes());
        bb2.flip();

        IoBuffer ioBuffer = new IoBuffer(bb1, bb2);

        // Move forward a bit
        ioBuffer.get();
        ioBuffer.get();

        // Clear
        ioBuffer.clear();

        // We should be back to the origin
        assertEquals(0, ioBuffer.position());
        assertEquals(8, ioBuffer.limit());
    }

    //-------------------------------------------------------------------------
    // Test the position() method
    // We will test that the position() metho returns the correct result in 
    // those cases :
    // 1) the buffer is empty : must return 0
    // 2) must return a value between 0 and limit()
    //-------------------------------------------------------------------------
    /**
     * Test the position method over an emptyIoBuffer
     */
    @Test
    public void testPositionEmptyBuffer() {
        IoBuffer ioBuffer = new IoBuffer();

        assertEquals(0, ioBuffer.position());
    }

    /**
     * Test the position method over a buffer
     */
    @Test
    public void testPositionBuffer() {
        ByteBuffer bb1 = ByteBuffer.allocate(4);
        bb1.put("012".getBytes());
        bb1.flip();

        ByteBuffer bb2 = ByteBuffer.allocate(4);
        bb2.put("3456".getBytes());
        bb2.flip();

        ByteBuffer bb3 = ByteBuffer.allocate(4);
        bb3.put("789".getBytes());
        bb3.flip();

        // The resulting buffer will be seen as "0123456789"
        IoBuffer ioBuffer = new IoBuffer(bb1, bb2, bb3);

        // Iterate and check the position
        for (int i = 0; i < ioBuffer.limit(); i++) {
            assertEquals(i, ioBuffer.position());
            ioBuffer.get();
        }
    }

    //-------------------------------------------------------------------------
    // Test the position(int) method
    // We will test many different cases
    // 1) a position() in an empty buffer
    // 2) a position() with a negative value
    // 3) a position() with a value above the limit
    // 4) a position() within the current buffer
    //  4-1) at the beginning of the current buffer
    //  4-2) at the end of the current buffer
    //  4-3) in the middle of the current buffer
    // 5) a position() before the current buffer
    // 6) a position() after the current buffer
    //-------------------------------------------------------------------------
    /**
     * Test the position method over an emptyIoBuffer
     */
    @Test(expected = IllegalArgumentException.class)
    public void testPositionIntEmptyBuffer() {
        IoBuffer ioBuffer = new IoBuffer();

        ioBuffer.position(0);
    }

    /**
     * Test the position method with a negative value
     */
    @Test(expected = IllegalArgumentException.class)
    public void testPositionNegativeValue() {
        ByteBuffer bb1 = ByteBuffer.allocate(4);
        bb1.put("0123".getBytes());
        bb1.flip();

        ByteBuffer bb2 = ByteBuffer.allocate(4);
        bb2.put("4567".getBytes());
        bb2.flip();

        ByteBuffer bb3 = ByteBuffer.allocate(4);
        bb3.put("89".getBytes());
        bb3.flip();

        IoBuffer ioBuffer = new IoBuffer(bb1, bb2, bb3);

        ioBuffer.position(-1);
    }

    /**
     * Test the position method with a value above the buffer size
     */
    @Test(expected = IllegalArgumentException.class)
    public void testPositionAboveValue() {
        ByteBuffer bb1 = ByteBuffer.allocate(4);
        bb1.put("012".getBytes());
        bb1.flip();

        ByteBuffer bb2 = ByteBuffer.allocate(4);
        bb2.put("3456".getBytes());
        bb2.flip();

        ByteBuffer bb3 = ByteBuffer.allocate(4);
        bb3.put("789".getBytes());
        bb3.flip();

        // The resulting buffer will be seen as "0123456789"
        IoBuffer ioBuffer = new IoBuffer(bb1, bb2, bb3);

        ioBuffer.position(10);
    }

    /**
     * Test the position method in the current buffer
     */
    @Test
    public void testPositionCurrentBuffer() {
        ByteBuffer bb1 = ByteBuffer.allocate(4);
        bb1.put("012".getBytes());
        bb1.flip();

        ByteBuffer bb2 = ByteBuffer.allocate(4);
        bb2.put("3456".getBytes());
        bb2.flip();

        ByteBuffer bb3 = ByteBuffer.allocate(4);
        bb3.put("789".getBytes());
        bb3.flip();

        // The resulting buffer will be seen as "0123456789"
        IoBuffer ioBuffer = new IoBuffer(bb1, bb2, bb3);

        // Set the position in the middle of bb2 (4-3)
        ioBuffer.position(5);

        assertEquals('5', ioBuffer.get());

        // Set the position at the beginning of bb2 (4-1)
        ioBuffer.position(3);

        assertEquals('3', ioBuffer.get());

        // Set the position at the end of bb2 (4-2)
        ioBuffer.position(6);

        assertEquals('6', ioBuffer.get());

        // Set a position before the current buffer (5)
        ioBuffer.position(2);
        assertEquals('2', ioBuffer.get());

        // Set a position after the current buffer (6)
        ioBuffer.position(7);
        assertEquals('7', ioBuffer.get());

        // Now, let's see if we can get all the elements correctly
        // if we set the position from 0 to end
        for (int i = 0; i < ioBuffer.limit(); i++) {
            ioBuffer.position(i);
            assertEquals('0' + i, ioBuffer.get());
        }

        // Same, in revert order
        for (int i = ioBuffer.limit() - 1; i >= 0; i--) {
            ioBuffer.position(i);
            assertEquals('0' + i, ioBuffer.get());
        }
    }
}
