import api from './api';
import { UserStatus, CustomStatus, StatusUpdate } from '../types/status';

// WebSocket connection constants
const WS_ENDPOINT = 'ws://localhost:8080/ws';
const STATUS_TOPIC = '/topic/status';
const STATUS_DESTINATION = '/app/status';

interface Message {
  body: string;
}

class StatusService {
  private webSocket: WebSocket | null = null;
  private connected: boolean = false;
  private statusCallbacks: Map<number, ((userId: number, status: StatusUpdate) => void)[]> = new Map();

  /**
   * Initialize WebSocket connection
   */
  initializeWebSocket(): Promise<void> {
    return new Promise((resolve, reject) => {
      if (this.webSocket && this.connected) {
        resolve();
        return;
      }

      this.webSocket = new WebSocket(WS_ENDPOINT);

      this.webSocket.onopen = () => {
        this.connected = true;
        this.setupWebSocketSubscription();
        resolve();
      };

      this.webSocket.onerror = (error) => {
        console.error('WebSocket error:', error);
        reject(new Error('WebSocket connection error'));
      };

      this.webSocket.onclose = () => {
        this.connected = false;
        console.log('WebSocket connection closed');
        // Attempt to reconnect after a delay
        setTimeout(() => this.initializeWebSocket(), 5000);
      };

      this.webSocket.onmessage = (event) => {
        try {
          const message = JSON.parse(event.data);
          if (message.destination === STATUS_TOPIC) {
            this.handleStatusUpdate(message);
          }
        } catch (error) {
          console.error('Error processing WebSocket message:', error);
        }
      };
    });
  }

  /**
   * Disconnect WebSocket
   */
  disconnect(): void {
    if (this.webSocket) {
      this.webSocket.close();
      this.webSocket = null;
      this.connected = false;
    }
  }

  /**
   * Handle status update messages
   */
  private handleStatusUpdate(message: any): void {
    try {
      const statusUpdate = JSON.parse(message.body);
      const { userId, status } = statusUpdate;
      
      // Notify all callbacks registered for this user
      const callbacks = this.statusCallbacks.get(userId) || [];
      callbacks.forEach(callback => callback(userId, status));
      
      // Notify global callbacks (registered with userId = 0)
      const globalCallbacks = this.statusCallbacks.get(0) || [];
      globalCallbacks.forEach(callback => callback(userId, status));
    } catch (error) {
      console.error('Error processing status update:', error);
    }
  }

  /**
   * Setup WebSocket subscription
   */
  private setupWebSocketSubscription(): void {
    if (!this.webSocket || !this.connected) {
      console.error('Cannot subscribe to status updates: WebSocket not connected');
      return;
    }

    // Subscribe to status topic
    const subscribeMessage = {
      command: 'SUBSCRIBE',
      destination: STATUS_TOPIC,
      id: 'status-sub'
    };
    
    this.webSocket.send(JSON.stringify(subscribeMessage));
  }

  /**
   * Update the current user's status
   */
  async updateStatus(status: UserStatus): Promise<void> {
    const update: StatusUpdate = { status };
    await api.put('/users/status', update);
    
    // Also send via WebSocket for real-time updates
    this.sendStatusUpdateViaWebSocket(update);
  }

  /**
   * Update the current user's status with a custom status message
   */
  async updateCustomStatus(status: UserStatus, customStatus: CustomStatus): Promise<void> {
    const update: StatusUpdate = { status, customStatus };
    await api.put('/users/status', update);
    
    // Also send via WebSocket for real-time updates
    this.sendStatusUpdateViaWebSocket(update);
  }

  /**
   * Send status update via WebSocket
   */
  private async sendStatusUpdateViaWebSocket(statusUpdate: StatusUpdate): Promise<void> {
    try {
      await this.initializeWebSocket();
      
      if (this.webSocket && this.connected) {
        const message = {
          command: 'SEND',
          destination: STATUS_DESTINATION,
          body: JSON.stringify(statusUpdate)
        };
        
        this.webSocket.send(JSON.stringify(message));
      }
    } catch (error) {
      console.error('Failed to send status update via WebSocket:', error);
    }
  }

  /**
   * Get a user's current status
   */
  async getUserStatus(userId: number): Promise<StatusUpdate> {
    const response = await api.get(`/users/${userId}/status`);
    return response.data;
  }

  /**
   * Clear the custom status
   */
  async clearCustomStatus(): Promise<void> {
    await api.delete('/users/status/custom');
    
    // Update WebSocket with status without custom status
    const currentStatus = await this.getCurrentUserStatus();
    this.sendStatusUpdateViaWebSocket({ status: currentStatus.status });
  }

  /**
   * Get current user's status
   */
  private async getCurrentUserStatus(): Promise<StatusUpdate> {
    const response = await api.get('/users/me/status');
    return response.data;
  }

  /**
   * Subscribe to status updates for a specific user
   * @param userId User ID to subscribe to (0 for all users)
   * @param callback Function to call when a status update is received
   * @returns Function to unsubscribe
   */
  subscribeToUserStatus(userId: number, callback: (userId: number, status: StatusUpdate) => void): () => void {
    // Initialize WebSocket if not already connected
    this.initializeWebSocket().catch(error => {
      console.error('Failed to initialize WebSocket:', error);
    });
    
    // Add callback to the map
    if (!this.statusCallbacks.has(userId)) {
      this.statusCallbacks.set(userId, []);
    }
    
    const callbacks = this.statusCallbacks.get(userId)!;
    callbacks.push(callback);
    
    // Return unsubscribe function
    return () => {
      const callbackIndex = callbacks.indexOf(callback);
      if (callbackIndex !== -1) {
        callbacks.splice(callbackIndex, 1);
      }
      
      // If no more callbacks for this user, remove the entry
      if (callbacks.length === 0) {
        this.statusCallbacks.delete(userId);
      }
      
      // If no more callbacks at all, disconnect WebSocket
      if (this.statusCallbacks.size === 0) {
        this.disconnect();
      }
    };
  }

  /**
   * Subscribe to all status updates
   * @param callback Function to call when any status update is received
   * @returns Function to unsubscribe
   */
  subscribeToStatusUpdates(callback: (userId: number, status: StatusUpdate) => void): () => void {
    return this.subscribeToUserStatus(0, callback);
  }
}

export default new StatusService(); 