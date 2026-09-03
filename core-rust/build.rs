fn main() {
    uniffi::generate_scaffolding("./src/paisapal.udl")
        .expect("Failed to generate UniFFI scaffolding from UDL file");
}
