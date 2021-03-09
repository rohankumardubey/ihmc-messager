package us.ihmc.messager;

import us.ihmc.messager.MessagerAPIFactory.MessagerAPI;
import us.ihmc.messager.MessagerAPIFactory.Topic;

import java.util.concurrent.atomic.AtomicReference;

/**
 * A basic interface for passing around to let things register their stuff
 * without exposing more volatile methods like start and close.
 * 
 * @author Sylvain Bertrand
 * @author Duncan Calvert
 */
public interface MessagerBasics
{
   /**
    * Sends data for a given topic.
    * 
    * @param topic          the topic of the data.
    * @param messageContent the data.
    */
   default <T> void submitMessage(Topic<T> topic, T messageContent)
   {
      submitMessage(new Message<>(topic, messageContent));
   }

   /**
    * Sends a message.
    * 
    * @param message the message to send.
    */
   <T> void submitMessage(Message<T> message);

   /**
    * Creates a variable which is to be automatically updated when this messager receives data destined
    * to the given topic.
    * 
    * @param topic        the topic to listen to.
    * @param initialValue the initial value of the newly created variable.
    * @return a variable that is updated automatically when receiving new data.
    */
   <T> AtomicReference<T> createInput(Topic<T> topic, T initialValue);

   /**
    * Attaches an existing AtomicReference as an input which is to be automatically updated when this messager
    * receives data destined to the given topic.
    *
    * @param topic        the topic to listen to.
    * @param input        an existing AtomicReference.
    */
   <T> void attachInput(Topic<T> topic, AtomicReference<T> input);

   /**
    * Creates a variable which is to be automatically updated when this messager receives data destined
    * to the given topic.
    * 
    * @param topic the topic to listen to.
    * @return a variable that is updated automatically when receiving new data.
    */
   default <T> AtomicReference<T> createInput(Topic<T> topic)
   {
      return createInput(topic, null);
   }

   /**
    * Removes an input that was previously created by this messager.
    * 
    * @param topic the topic the input is listening to.
    * @param input the input to be removed from this messager.
    * @return {@code true} if the internal list of inputs was modified by this operation, {@code false}
    *         otherwise.
    */
   <T> boolean removeInput(Topic<T> topic, AtomicReference<T> input);

   /**
    * Registers a listener to be notified when new data is received for the given topic.
    * 
    * @param topic    the topic to listen to.
    * @param listener the listener to be registered.
    */
   <T> void registerTopicListener(Topic<T> topic, TopicListener<T> listener);

   /**
    * Removes a listener that was previously registered to this messager.
    * 
    * @param topic    the topic the listener is listening to.
    * @param listener the listener to be removed.
    * @return {@code true} if the internal list of inputs was modified by this operation, {@code false}
    *         otherwise.
    */
   <T> boolean removeTopicListener(Topic<T> topic, TopicListener<T> listener);

   /**
    * Tests whether this messager is currently open, i.e. whether messages can be sent and received.
    * 
    * @return {@code true} if this messager is open, {@code false} if it is closed.
    */
   boolean isMessagerOpen();

   /**
    * Registers a new listener that is to be notified when the state of this messager changes.
    * 
    * @param listener the listener to register.
    */
   void registerMessagerStateListener(MessagerStateListener listener);

   /**
    * Removes a listener previously registered to this messager.
    * 
    * @param listener the listener to remove.
    * @return {@code true} if the internal list of inputs was modified by this operation, {@code false}
    *         otherwise.
    */
   boolean removeMessagerStateListener(MessagerStateListener listener);

   /**
    * Gets the API used by this messager.
    * 
    * @return this messger's API.
    */
   MessagerAPI getMessagerAPI();
}