const express = require("express");
const app = express();

app.use(express.static("testing-aristotle"));

app.use(express.json());

app.get("/api/users", (req, res) => {
    res.json([]);
});

app.post("/api/register", (req, res) => {
    res.send("Registered");
});

app.listen(8080, () => {
    console.log("Server running on http://localhost:8080");
});