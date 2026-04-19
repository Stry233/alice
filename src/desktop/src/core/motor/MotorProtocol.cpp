#include "core/motor/MotorProtocol.h"

#include <regex>
#include <sstream>
#include <algorithm>
#include <cctype>

namespace alice {
namespace MotorProtocol {

namespace {
    const std::regex kPositionPattern(R"(OK:POS=(\d+))");
    const std::regex kDestPattern(R"(OK:DEST=([0-9A-Fa-f]{4}))");
    const std::regex kScanPattern(R"(OK:SCAN=([0-9A-Fa-f]{4}))");
    const std::regex kStatusPattern(R"(Current=(\d+))");

    std::string trim(const std::string &s) {
        auto begin = std::find_if_not(s.begin(), s.end(),
                                       [](unsigned char c) { return std::isspace(c); });
        auto end = std::find_if_not(s.rbegin(), s.rend(),
                                     [](unsigned char c) { return std::isspace(c); }).base();
        return (begin < end) ? std::string(begin, end) : std::string();
    }
} // anonymous

std::string formatCommand(const std::string &command) {
    return command + "\r\n";
}

std::string formatPositionCommand(int position) {
    return formatCommand(std::string(kCmdPosition) + " " + std::to_string(clampPosition(position)));
}

std::string formatDestCommand(int address) {
    int high = (address >> 8) & 0xFF;
    int low  = address & 0xFF;
    return formatCommand(std::string(kCmdDest) + " " + std::to_string(high) + " " + std::to_string(low));
}

std::string formatScanCommand(int address) {
    int high = (address >> 8) & 0xFF;
    int low  = address & 0xFF;
    return formatCommand(std::string(kCmdScan) + " " + std::to_string(high) + " " + std::to_string(low));
}

MotorResponse parseResponse(const std::string &response) {
    std::string trimmed = trim(response);
    std::smatch match;

    // OK:POS=<value>
    if (trimmed.rfind("OK:POS=", 0) == 0) {
        if (std::regex_search(trimmed, match, kPositionPattern)) {
            try { return MotorPositionResponse{std::stoi(match[1].str())}; }
            catch (...) {}
        }
        return MotorUnknownResponse{trimmed};
    }

    // OK:DEST=<hex>
    if (trimmed.rfind("OK:DEST=", 0) == 0) {
        if (std::regex_search(trimmed, match, kDestPattern)) {
            auto addr = parseHexAddress(match[1].str());
            if (addr) return MotorDestResponse{*addr};
        }
        return MotorUnknownResponse{trimmed};
    }

    // OK:SCAN=<hex>
    if (trimmed.rfind("OK:SCAN=", 0) == 0) {
        if (std::regex_search(trimmed, match, kScanPattern)) {
            auto addr = parseHexAddress(match[1].str());
            if (addr) return MotorScanResponse{*addr};
        }
        return MotorUnknownResponse{trimmed};
    }

    // Current=<value>
    if (trimmed.find("Current=") != std::string::npos) {
        if (std::regex_search(trimmed, match, kStatusPattern)) {
            try { return MotorStatusResponse{std::stoi(match[1].str()), trimmed}; }
            catch (...) {}
        }
        return MotorUnknownResponse{trimmed};
    }

    // CALIBRATED
    if (trimmed.find("CALIBRATED") != std::string::npos) {
        return MotorCalibratedResponse{};
    }

    // ERROR:
    if (trimmed.rfind("ERROR:", 0) == 0) {
        return MotorErrorResponse{trimmed.substr(6)};
    }

    // Ready
    if (trimmed.find("Ready") != std::string::npos) {
        return MotorReadyResponse{};
    }

    return MotorUnknownResponse{trimmed};
}

std::optional<int> parseHexAddress(const std::string &hex) {
    try {
        return std::stoi(hex, nullptr, 16);
    } catch (...) {
        return std::nullopt;
    }
}

std::string formatAddressHex(int address) {
    char buf[8];
    std::snprintf(buf, sizeof(buf), "%04X", address & 0xFFFF);
    return std::string(buf);
}

} // namespace MotorProtocol
} // namespace alice
