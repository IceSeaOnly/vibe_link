import Foundation

public enum H264AnnexB {
    public static let startCode = Data([0x00, 0x00, 0x00, 0x01])

    public static func wrap(nalUnits: [Data]) -> Data {
        var output = Data()
        for nalUnit in nalUnits where !nalUnit.isEmpty {
            output.append(startCode)
            output.append(nalUnit)
        }
        return output
    }
}
