const express = require('express');
const cors = require('cors');
const { Pool } = require('pg');
const fs = require('fs');
const path = require('path');
require('dotenv').config();

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());

// PostgreSQL Pool Connection
const pool = new Pool({
  user: process.env.PGUSER || process.env.USER,
  host: process.env.PGHOST || 'localhost',
  database: process.env.PGDATABASE || 'spotify_db',
  password: process.env.PGPASSWORD || '',
  port: process.env.PGPORT || 5432,
});

// Auto-initialize tables from schema.sql
async function initDb() {
  try {
    const schemaPath = path.join(__dirname, 'schema.sql');
    if (fs.existsSync(schemaPath)) {
      const sql = fs.readFileSync(schemaPath, 'utf8');
      await pool.query(sql);
      console.log('✓ PostgreSQL schema synchronized successfully.');
    }
  } catch (err) {
    console.error('Error initializing schema:', err.message);
  }
}

// Health check endpoint
app.get('/api/health', async (req, res) => {
  try {
    await pool.query('SELECT 1');
    res.json({ status: 'ok', database: 'connected', timestamp: new Date().toISOString() });
  } catch (err) {
    res.status(500).json({ status: 'error', database: err.message });
  }
});

// Sign Up: username + password (SHA-256 hash passed from app or computed)
app.post('/api/auth/signup', async (req, res) => {
  try {
    const { username, passwordHash } = req.body;
    if (!username || !passwordHash) {
      return res.status(400).json({ error: 'Username and passwordHash are required' });
    }

    const cleanUsername = username.trim();
    if (cleanUsername.length < 3 || cleanUsername.length > 30) {
      return res.status(400).json({ error: 'Username must be 3-30 characters' });
    }

    // Check if username already exists (case-insensitive)
    const existing = await pool.query('SELECT id FROM users WHERE LOWER(username) = LOWER($1)', [cleanUsername]);
    if (existing.rows.length > 0) {
      return res.status(409).json({ error: `Username "${cleanUsername}" is already taken.` });
    }

    const id = 'user_' + Date.now();
    const letter = cleanUsername[0].toUpperCase();
    const colors = ['#1DB954', '#FF5722', '#9C27B0', '#2196F3'];
    const countRes = await pool.query('SELECT COUNT(*) FROM users');
    const colorHex = colors[parseInt(countRes.rows[0].count, 10) % colors.length];

    const insertQuery = `
      INSERT INTO users (id, username, password_hash, avatar_letter, avatar_color_hex, is_premium, is_student, selected_genres, selected_artists, has_completed_onboarding)
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
      RETURNING id, username, avatar_letter, avatar_color_hex, is_premium, is_student, selected_genres, selected_artists, has_completed_onboarding, created_at
    `;
    const values = [id, cleanUsername, passwordHash, letter, colorHex, true, false, [], [], false];
    const result = await pool.query(insertQuery, values);
    const row = result.rows[0];

    res.status(201).json({
      id: row.id,
      username: row.username,
      name: row.username,
      avatarLetter: row.avatar_letter,
      avatarColorHex: row.avatar_color_hex,
      isPremium: row.is_premium,
      isStudent: row.is_student,
      isGuest: false,
      selectedGenres: row.selected_genres || [],
      selectedArtists: row.selected_artists || [],
      hasCompletedOnboarding: row.has_completed_onboarding
    });
  } catch (err) {
    console.error('Signup error:', err);
    res.status(500).json({ error: 'Internal server error: ' + err.message });
  }
});

// Log In: username + passwordHash
app.post('/api/auth/login', async (req, res) => {
  try {
    const { username, passwordHash } = req.body;
    if (!username || !passwordHash) {
      return res.status(400).json({ error: 'Username and passwordHash are required' });
    }

    const cleanUsername = username.trim();
    const query = `
      SELECT id, username, password_hash, avatar_letter, avatar_color_hex, is_premium, is_student, selected_genres, selected_artists, has_completed_onboarding
      FROM users
      WHERE LOWER(username) = LOWER($1)
    `;
    const result = await pool.query(query, [cleanUsername]);
    if (result.rows.length === 0) {
      return res.status(404).json({ error: `No account found with username "${cleanUsername}"` });
    }

    const row = result.rows[0];
    if (row.password_hash !== passwordHash) {
      return res.status(401).json({ error: 'Incorrect password. Please try again.' });
    }

    res.json({
      id: row.id,
      username: row.username,
      name: row.username,
      avatarLetter: row.avatar_letter,
      avatarColorHex: row.avatar_color_hex,
      isPremium: row.is_premium,
      isStudent: row.is_student,
      isGuest: false,
      selectedGenres: row.selected_genres || [],
      selectedArtists: row.selected_artists || [],
      hasCompletedOnboarding: row.has_completed_onboarding
    });
  } catch (err) {
    console.error('Login error:', err);
    res.status(500).json({ error: 'Internal server error: ' + err.message });
  }
});

// Update Interests (First-time onboarding)
app.post('/api/auth/interests', async (req, res) => {
  try {
    const { userId, genres, artists } = req.body;
    if (!userId) {
      return res.status(400).json({ error: 'userId is required' });
    }

    const query = `
      UPDATE users
      SET selected_genres = $1, selected_artists = $2, has_completed_onboarding = true
      WHERE id = $3
      RETURNING id, username, avatar_letter, avatar_color_hex, is_premium, is_student, selected_genres, selected_artists, has_completed_onboarding
    `;
    const result = await pool.query(query, [genres || [], artists || [], userId]);
    if (result.rows.length === 0) {
      return res.status(404).json({ error: 'User not found' });
    }

    const row = result.rows[0];
    res.json({
      id: row.id,
      username: row.username,
      name: row.username,
      avatarLetter: row.avatar_letter,
      avatarColorHex: row.avatar_color_hex,
      isPremium: row.is_premium,
      isStudent: row.is_student,
      isGuest: false,
      selectedGenres: row.selected_genres || [],
      selectedArtists: row.selected_artists || [],
      hasCompletedOnboarding: row.has_completed_onboarding
    });
  } catch (err) {
    console.error('Update interests error:', err);
    res.status(500).json({ error: 'Internal server error: ' + err.message });
  }
});

// Get all registered users (for account switching)
app.get('/api/auth/users', async (req, res) => {
  try {
    const result = await pool.query(`
      SELECT id, username, avatar_letter, avatar_color_hex, is_premium, is_student, selected_genres, selected_artists, has_completed_onboarding
      FROM users
      ORDER BY created_at DESC
    `);
    const users = result.rows.map(row => ({
      id: row.id,
      username: row.username,
      name: row.username,
      avatarLetter: row.avatar_letter,
      avatarColorHex: row.avatar_color_hex,
      isPremium: row.is_premium,
      isStudent: row.is_student,
      isGuest: false,
      selectedGenres: row.selected_genres || [],
      selectedArtists: row.selected_artists || [],
      hasCompletedOnboarding: row.has_completed_onboarding
    }));
    res.json(users);
  } catch (err) {
    console.error('Get users error:', err);
    res.status(500).json({ error: 'Internal server error: ' + err.message });
  }
});

// Start server
app.listen(PORT, async () => {
  console.log(`🚀 Spotify Auth Service listening on port ${PORT}`);
  await initDb();
});
