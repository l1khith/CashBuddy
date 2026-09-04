fn main() {
    uniffi::generate_scaffolding("./src/cashbuddy.udl")
        .expect("Failed to generate UniFFI scaffolding from UDL file");
}
