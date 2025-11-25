/*
 * Tilta Nucleus Nano 2 Motor Control with USB CDC-ACM Interface
 *
 * This integrates USB CDC-ACM communication to control motor position.
 * Commands:
 * - "POS <value>" - Set motor position (0-4095)
 * - "DEST <high> <low>" - Set destination address (0-255 each byte)
 * - "SCAN <high> <low>" - Test a specific destination address
 * - "GETDEST" - Get current destination address
 * - "STATUS" - Get current status
 * - "VERSION" - Get firmware version
 * - "HELP" - Show available commands
 */

#include <zephyr/kernel.h>
#include <zephyr/device.h>
#include <zephyr/drivers/gpio.h>
#include <zephyr/drivers/uart.h>
#include <zephyr/sys/ring_buffer.h>
#include <zephyr/usb/usb_device.h>
#include <nrf_802154.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>

typedef uint8_t byte;

/* LED definitions */
static const struct gpio_dt_spec led = GPIO_DT_SPEC_GET(DT_ALIAS(led0), gpios);
static const struct gpio_dt_spec led_r = GPIO_DT_SPEC_GET(DT_ALIAS(led1_red), gpios);
static const struct gpio_dt_spec led_b = GPIO_DT_SPEC_GET(DT_ALIAS(led1_blue), gpios);

/* Ring buffer for transmit data */
RING_BUF_DECLARE(tx_ringbuf, 1024);

/* Command buffer */
static char cmd_buffer[256];
static int cmd_index = 0;

/* Device status */
static uint32_t message_count = 0;
static const struct device *cdc_dev;

/* Motor control variables */
static int target_position = 2048;  /* Default to middle position */
static int current_position = 2048;
static bool position_changed = false;
static uint32_t packets_sent = 0;
static byte sequence_number = 0;

/* Destination address (16-bit, stored as two bytes) */
static byte dest_addr_high = 0xFF;  /* Default to broadcast */
static byte dest_addr_low = 0xFF;

/* Motor packet template
 * Byte layout:
 * [0]    = 0x0f - Frame length
 * [1-2]  = 0x61 0x88 - Frame control
 * [3]    = Sequence number
 * [4-5]  = 0xE4 0x3D - Destination PAN ID
 * [6-7]  = Destination address (dynamic)
 * [8-9]  = 0x96 0xF0 - Source address
 * [10-12]= Position data + flags
 * [13]   = Checksum
 * [14-15]= Padding
 */
static byte data_to_transmit[] = "\x0f\x61\x88\x00\xE4\x3D\xFF\xFF\x96\xF0\x44\x05\x00\xB7\x00\x00";

/* Function prototypes */
static void process_command(char *cmd);
static void send_string(const char *str);
static void set_pos(byte* data, int val);
static void set_dest(byte* data, byte high, byte low);
static void send_scan_packet(byte high, byte low, int test_position);
static void motor_control_thread(void);

/* Scan test position - uncommon value, safe from lens extremes */
#define SCAN_TEST_POSITION 1234

/* Set motor position in packet */
static void set_pos(byte* data, int val)
{
    data[10] = 0x40 | (byte)((val >> 8) & 0x0F);
    data[11] = (byte)(val & 0xFF);
    data[13] = 0 - data[10] - data[11] - data[12];
}

/* Set destination address in packet */
static void set_dest(byte* data, byte high, byte low)
{
    data[6] = high;
    data[7] = low;
}

/* Send a scan packet to test a specific address */
static void send_scan_packet(byte high, byte low, int test_position)
{
    /* Create a temporary packet for scanning */
    byte scan_packet[16];
    memcpy(scan_packet, data_to_transmit, sizeof(scan_packet));

    /* Set the test destination address */
    scan_packet[6] = high;
    scan_packet[7] = low;

    /* Set test position and sequence number */
    set_pos(scan_packet, test_position);
    scan_packet[3] = sequence_number++;

    /* Transmit the scan packet */
    nrf_802154_transmit_raw(scan_packet, NULL);
    packets_sent++;
}

/* UART interrupt handler */
static void interrupt_handler(const struct device *dev, void *user_data)
{
    ARG_UNUSED(user_data);

    while (uart_irq_update(dev) && uart_irq_is_pending(dev)) {
        if (uart_irq_rx_ready(dev)) {
            uint8_t c;
            
            while (uart_fifo_read(dev, &c, 1) == 1) {
                /* Process byte */
                if (c == '\n' || c == '\r') {
                    if (cmd_index > 0) {
                        cmd_buffer[cmd_index] = '\0';
                        process_command(cmd_buffer);
                        cmd_index = 0;
                        send_string("> ");
                    }
                } else if (cmd_index < sizeof(cmd_buffer) - 1) {
                    cmd_buffer[cmd_index++] = c;
                    /* Echo character back */
                    uart_poll_out(dev, c);
                }
            }
        }

        if (uart_irq_tx_ready(dev)) {
            uint8_t buffer[64];
            int rb_len;

            rb_len = ring_buf_get(&tx_ringbuf, buffer, sizeof(buffer));
            if (rb_len) {
                uart_fifo_fill(dev, buffer, rb_len);
            } else {
                uart_irq_tx_disable(dev);
            }
        }
    }
}

/* Process received commands */
static void process_command(char *cmd)
{
    char response[256];
    char *token;
    
    message_count++;

    /* Parse command */
    token = strtok(cmd, " ");
    if (!token) return;

    /* Convert to uppercase for case-insensitive comparison */
    for (int i = 0; token[i]; i++) {
        if (token[i] >= 'a' && token[i] <= 'z') {
            token[i] = token[i] - 'a' + 'A';
        }
    }

    if (strcmp(token, "POS") == 0) {
        /* Get position value */
        token = strtok(NULL, " ");
        if (token) {
            int pos = atoi(token);
            if (pos >= 0 && pos <= 4095) {
                target_position = pos;
                position_changed = true;
                snprintf(response, sizeof(response),
                         "\nOK:POS=%d\r\n", pos);
                send_string(response);
            } else {
                send_string("ERROR: Position must be 0-4095\r\n");
            }
        } else {
            send_string("ERROR: Usage: POS <value>\r\n");
        }
    } else if (strcmp(token, "DEST") == 0) {
        /* Set destination address: DEST <high> <low> */
        char *high_str = strtok(NULL, " ");
        char *low_str = strtok(NULL, " ");
        if (high_str && low_str) {
            int high = atoi(high_str);
            int low = atoi(low_str);
            if (high >= 0 && high <= 255 && low >= 0 && low <= 255) {
                dest_addr_high = (byte)high;
                dest_addr_low = (byte)low;
                /* Update the packet template with new destination */
                set_dest(data_to_transmit, dest_addr_high, dest_addr_low);
                snprintf(response, sizeof(response),
                         "\nOK:DEST=%02X%02X\r\n", dest_addr_high, dest_addr_low);
                send_string(response);
            } else {
                send_string("ERROR: Address bytes must be 0-255\r\n");
            }
        } else {
            send_string("ERROR: Usage: DEST <high> <low>\r\n");
        }
    } else if (strcmp(token, "SCAN") == 0) {
        /* Send test packet to specific address: SCAN <high> <low>
         * Uses SCAN_TEST_POSITION (1234) to cause visible motor movement
         * if the address is correct. This position is:
         * - Uncommon (unlikely to be current position)
         * - Safe (not at lens extremes 0 or 4095)
         */
        char *high_str = strtok(NULL, " ");
        char *low_str = strtok(NULL, " ");
        if (high_str && low_str) {
            int high = atoi(high_str);
            int low = atoi(low_str);
            if (high >= 0 && high <= 255 && low >= 0 && low <= 255) {
                /* Send multiple scan packets with test position for reliability */
                for (int i = 0; i < 10; i++) {
                    send_scan_packet((byte)high, (byte)low, SCAN_TEST_POSITION);
                }
                snprintf(response, sizeof(response),
                         "\nOK:SCAN=%02X%02X@%d\r\n", high, low, SCAN_TEST_POSITION);
                send_string(response);
            } else {
                send_string("ERROR: Address bytes must be 0-255\r\n");
            }
        } else {
            send_string("ERROR: Usage: SCAN <high> <low>\r\n");
        }
    } else if (strcmp(token, "GETDEST") == 0) {
        /* Get current destination address */
        snprintf(response, sizeof(response),
                 "\nOK:DEST=%02X%02X\r\n", dest_addr_high, dest_addr_low);
        send_string(response);
    } else if (strcmp(token, "STATUS") == 0) {
        snprintf(response, sizeof(response),
                 "STATUS: Target=%d, Current=%d, Dest=%02X%02X, Packets=%u, Messages=%u\r\n",
                 target_position, current_position, dest_addr_high, dest_addr_low, packets_sent, message_count);
        send_string(response);
    } else if (strcmp(token, "VERSION") == 0) {
        send_string("VERSION: Tilta Motor Control v2.0\r\n");
    } else if (strcmp(token, "HELP") == 0) {
        send_string("Commands:\r\n");
        send_string("  POS <value>      - Set motor position (0-4095)\r\n");
        send_string("  DEST <hi> <lo>   - Set destination address (0-255 each)\r\n");
        send_string("  SCAN <hi> <lo>   - Test a specific address\r\n");
        send_string("  GETDEST          - Get current destination\r\n");
        send_string("  STATUS           - Get current status\r\n");
        send_string("  VERSION          - Get firmware version\r\n");
        send_string("  HELP             - Show this help\r\n");
    } else {
        snprintf(response, sizeof(response), "ERROR: Unknown command '%s'\r\n", token);
        send_string(response);
    }
}

/* Send string to USB CDC-ACM */
static void send_string(const char *str)
{
    int len = strlen(str);
    int rb_len;

    rb_len = ring_buf_put(&tx_ringbuf, (uint8_t *)str, len);
    if (rb_len < len) {
        /* Buffer full, drop message */
    }

    uart_irq_tx_enable(cdc_dev);
}

/* Motor control thread */
K_THREAD_DEFINE(motor_thread, 1024, motor_control_thread, NULL, NULL, NULL, 5, 0, 0);

static void motor_control_thread(void)
{
    /* Initialize 802.15.4 radio */
    nrf_802154_init();
    nrf_802154_channel_set(12);

    int noise_counter = 0;
    
    while (1) {
        /* Smooth position changes */
        if (current_position != target_position) {
            int diff = target_position - current_position;
            if (abs(diff) > 50) {
                /* Large movement - move faster */
                current_position += (diff > 0) ? 50 : -50;
            } else {
                /* Small movement - move directly */
                current_position = target_position;
            }
            position_changed = true;
        }

        if (position_changed || (noise_counter % 10) == 0) {
            /* Add noise to keep motor at full speed */
            int pos_with_noise = current_position + (noise_counter % 2);
            
            /* Update packet */
            set_pos(data_to_transmit, pos_with_noise);
            data_to_transmit[3] = sequence_number++;
            
            /* Transmit */
            nrf_802154_transmit_raw(data_to_transmit, NULL);
            packets_sent++;
            
            /* Visual feedback */
            gpio_pin_set_dt(&led_r, (current_position > 2048));
            
            position_changed = false;
        }
        
        noise_counter++;
        k_msleep(10);  /* 100Hz update rate */
    }
}

void main(void)
{
    uint32_t dtr = 0;
    int ret;
    char response[64];

    /* Configure LEDs */
    if (!(gpio_is_ready_dt(&led) && gpio_is_ready_dt(&led_r) && gpio_is_ready_dt(&led_b))) {
        return;
    }

    gpio_pin_configure_dt(&led, GPIO_OUTPUT_INACTIVE);
    gpio_pin_configure_dt(&led_r, GPIO_OUTPUT_INACTIVE);
    gpio_pin_configure_dt(&led_b, GPIO_OUTPUT_INACTIVE);

    /* Turn on blue LED to indicate startup */
    gpio_pin_set_dt(&led_b, 1);

    /* Get the CDC ACM device */
    cdc_dev = DEVICE_DT_GET_ONE(zephyr_cdc_acm_uart);
    if (!device_is_ready(cdc_dev)) {
        /* Flash red LED to indicate error */
        while (1) {
            gpio_pin_toggle_dt(&led_r);
            k_msleep(500);
        }
    }

    /* Enable USB */
    ret = usb_enable(NULL);
    if (ret != 0 && ret != -EALREADY) {
        /* Flash red LED to indicate error */
        while (1) {
            gpio_pin_toggle_dt(&led_r);
            k_msleep(250);
        }
    }

    /* Wait for DTR */
    while (!dtr) {
        uart_line_ctrl_get(cdc_dev, UART_LINE_CTRL_DTR, &dtr);
        k_sleep(K_MSEC(100));
        /* Blink blue LED while waiting */
        gpio_pin_toggle_dt(&led_b);
    }
    
    /* Connection established - turn off blue, turn on green */
    gpio_pin_set_dt(&led_b, 0);
    gpio_pin_set_dt(&led, 1);

    /* Configure interrupt and callback */
    uart_irq_callback_set(cdc_dev, interrupt_handler);

    /* Enable RX interrupts */
    uart_irq_rx_enable(cdc_dev);

    /* Send welcome message */
    k_sleep(K_MSEC(100));
    
    send_string("\r\n========================================\r\n");
    send_string("Tilta Nucleus Nano 2 Motor Control v2.0\r\n");
    send_string("========================================\r\n");
    send_string("Commands: POS, DEST, SCAN, GETDEST, STATUS, HELP\r\n");
    snprintf(response, sizeof(response), "Destination: %02X%02X\r\n", dest_addr_high, dest_addr_low);
    send_string(response);
    send_string("Ready!\r\n> ");

    /* Main loop */
    while (1) {
        /* Monitor USB connection */
        uint32_t dtr_current;
        uart_line_ctrl_get(cdc_dev, UART_LINE_CTRL_DTR, &dtr_current);
        
        if (!dtr_current) {
            /* Lost connection - turn off green LED */
            gpio_pin_set_dt(&led, 0);
        } else if (dtr_current && gpio_pin_get_dt(&led) == 0) {
            /* Reconnected - turn on green LED */
            gpio_pin_set_dt(&led, 1);
            send_string("\r\nReconnected\r\n> ");
        }

        k_sleep(K_MSEC(500));
    }
}