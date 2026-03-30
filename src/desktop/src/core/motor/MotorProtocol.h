#pragma once

#include <string>
#include <variant>
#include <optional>
#include <cstdint>

namespace alice {

// ── Motor responses ──────────────────────────────────────────────────

struct MotorPositionResponse { int position; };
struct MotorStatusResponse   { int position; std::string fullStatus; };
struct MotorDestResponse     { int address; };
struct MotorScanResponse     { int address; };
struct MotorCalibratedResponse {};
struct MotorReadyResponse {};
struct MotorErrorResponse    { std::string message; };
struct MotorUnknownResponse  { std::string raw; };

using MotorResponse = std::variant<
    MotorPositionResponse,
    MotorStatusResponse,
    MotorDestResponse,
    MotorScanResponse,
    MotorCalibratedResponse,
    MotorReadyResponse,
    MotorErrorResponse,
    MotorUnknownResponse
>;

// ── Protocol ─────────────────────────────────────────────────────────

/**
 * Motor controller protocol definitions and response parsing.
 * Ported from MotorProtocol.kt — identical command/response format.
 */
namespace MotorProtocol {

    // Serial parameters
    constexpr int kBaudRate = 115200;
    constexpr int kDataBits = 8;
    constexpr int kStopBits = 1;
    // Parity: None

    // Motor position range
    constexpr int kMinPosition = 0;
    constexpr int kMaxPosition = 4095;

    // Command names
    constexpr const char *kCmdPosition  = "POS";
    constexpr const char *kCmdStatus    = "STATUS";
    constexpr const char *kCmdHelp      = "HELP";
    constexpr const char *kCmdCalibrate = "CALIBRATE";
    constexpr const char *kCmdDest      = "DEST";
    constexpr const char *kCmdScan      = "SCAN";
    constexpr const char *kCmdGetDest   = "GETDEST";

    /** Format a raw command string for serial transmission (appends \r\n). */
    std::string formatCommand(const std::string &command);

    /** Build a POS command: "POS <value>\r\n". */
    std::string formatPositionCommand(int position);

    /** Build a DEST command: "DEST <high> <low>\r\n". */
    std::string formatDestCommand(int address);

    /** Build a SCAN command: "SCAN <high> <low>\r\n". */
    std::string formatScanCommand(int address);

    /** Parse a single response line from the motor controller. */
    MotorResponse parseResponse(const std::string &response);

    /** Parse a hex address string (e.g. "FFFF") to integer. */
    std::optional<int> parseHexAddress(const std::string &hex);

    /** Format an address as a 4-digit uppercase hex string. */
    std::string formatAddressHex(int address);

    /** Validate a position value. */
    inline bool isValidPosition(int pos) { return pos >= kMinPosition && pos <= kMaxPosition; }

    /** Clamp to valid range. */
    inline int clampPosition(int pos) {
        return (pos < kMinPosition) ? kMinPosition
             : (pos > kMaxPosition) ? kMaxPosition
             : pos;
    }

} // namespace MotorProtocol
} // namespace alice
