// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "VibeLinkServer",
    platforms: [
        .macOS(.v13)
    ],
    products: [
        .executable(name: "VibeLinkServer", targets: ["VibeLinkServer"])
    ],
    targets: [
        .target(name: "VibeLinkServerCore"),
        .executableTarget(
            name: "VibeLinkServer",
            dependencies: ["VibeLinkServerCore"]
        ),
        .testTarget(
            name: "VibeLinkServerCoreTests",
            dependencies: ["VibeLinkServerCore"]
        )
    ]
)
