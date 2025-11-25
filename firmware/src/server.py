#!/usr/bin/env python3
"""
Tilta Nucleus Nano 2 Motor Control via USB CDC-ACM

This script provides a simple interface to control the motor position.
"""

import serial
import serial.tools.list_ports
import time
import sys
import threading

def list_devices():
    """List all serial devices"""
    ports = list(serial.tools.list_ports.comports())
    devices = []
    
    for i, port in enumerate(ports):
        print(f"{i}: {port.device} - {port.description}")
        if "Tilta Motor Control" in str(port.description) or \
           "Straw Lab" in str(port.manufacturer):
            devices.append(port)
    
    return ports, devices

def connect_to_device(port_name=None):
    """Connect to the motor controller"""
    if not port_name:
        ports, devices = list_devices()
        
        if devices:
            # Auto-select if only one device found
            if len(devices) == 1:
                port_name = devices[0].device
                print(f"\nAuto-detected motor controller on {port_name}")
            else:
                # Multiple devices found
                print(f"\nFound {len(devices)} motor controllers:")
                for i, dev in enumerate(devices):
                    print(f"  {i}: {dev.device}")
                
                try:
                    selection = int(input("Select device number: "))
                    if 0 <= selection < len(devices):
                        port_name = devices[selection].device
                    else:
                        print("Invalid selection!")
                        return None
                except ValueError:
                    print("Invalid input!")
                    return None
        else:
            # No auto-detected devices
            if not ports:
                print("No serial ports found!")
                return None
            
            try:
                selection = int(input("\nNo motor controller found. Select port number: "))
                if 0 <= selection < len(ports):
                    port_name = ports[selection].device
                else:
                    print("Invalid selection!")
                    return None
            except ValueError:
                print("Invalid input!")
                return None
    
    try:
        ser = serial.Serial(port_name, 115200, timeout=1)
        print(f"Connected to {port_name}")
        return ser
    except serial.SerialException as e:
        print(f"Failed to connect to {port_name}: {e}")
        return None

def read_responses(ser):
    """Read responses from the device in a separate thread"""
    while ser.is_open:
        try:
            if ser.in_waiting:
                response = ser.read(ser.in_waiting).decode('utf-8', errors='ignore')
                print(response, end='', flush=True)
        except:
            break
        time.sleep(0.01)

def send_command(ser, command):
    """Send a command to the device"""
    ser.write(f"{command}\r\n".encode())
    time.sleep(0.1)  # Allow time for response

def demo_mode(ser):
    """Run a demo sequence"""
    print("\n=== DEMO MODE ===")
    print("Running motor demo sequence...")
    
    positions = [
        (0, "Minimum position"),
        (1024, "Quarter position"),
        (2048, "Center position"),
        (3072, "Three-quarter position"),
        (4095, "Maximum position"),
        (2048, "Back to center")
    ]
    
    for pos, desc in positions:
        print(f"\n{desc}...")
        send_command(ser, f"POS {pos}")
        time.sleep(2)
    
    print("\nDemo complete!")

def interactive_mode(ser):
    """Interactive command mode"""
    print("\n=== INTERACTIVE MODE ===")
    print("Commands:")
    print("  POS <0-4095>  - Set motor position")
    print("  STATUS        - Get current status")
    print("  HELP          - Show device help")
    print("  DEMO          - Run demo sequence")
    print("  QUIT          - Exit")
    print()
    
    # Start response reader thread
    reader_thread = threading.Thread(target=read_responses, args=(ser,), daemon=True)
    reader_thread.start()
    
    while True:
        try:
            cmd = input().strip()
            
            if cmd.upper() == "QUIT":
                break
            elif cmd.upper() == "DEMO":
                demo_mode(ser)
            elif cmd:
                send_command(ser, cmd)
        except KeyboardInterrupt:
            print("\nExiting...")
            break

def main():
    print("Tilta Nucleus Nano 2 Motor Control")
    print("===================================")
    
    # Connect to device
    ser = connect_to_device()
    if not ser:
        return
    
    # Wait for device to initialize
    time.sleep(1)
    
    # Clear any initial data
    if ser.in_waiting:
        ser.read(ser.in_waiting)
    
    # Check if demo mode requested
    if len(sys.argv) > 1 and sys.argv[1].lower() == "demo":
        demo_mode(ser)
    else:
        interactive_mode(ser)
    
    ser.close()
    print("Disconnected")

if __name__ == "__main__":
    main()