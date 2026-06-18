const canvas = document.getElementById('puzzleCanvas');
const ctx = canvas.getContext('2d');

const statusBadge = document.getElementById('conn-status');
const statusText = document.getElementById('status-text');

const userCountLabel = document.getElementById('node-count');

let currentImageId = null;
const chatInput = document.getElementById('chat-input');
const chatBox = document.getElementById('chat-box');

const tileSize = 100;
const gridStartX = 250;
const gridStartY = 50;

const rows = 4;
const cols = 4;
let pieces = [];
let mySessionId = null;
let selectedPiece = null;
let dragOffsetX = 0;
let dragOffsetY = 0;

let isAnimatingWin = false;


const puzzleImg = new Image();

const remoteCursors = {};


puzzleImg.crossOrigin = "Anonymous";
puzzleImg.onload = () => {
    if (!isAnimatingWin) drawSystem();
};

const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
const ws = new WebSocket(`${protocol}//${window.location.host}/ws`);

const connectionTimeout = setTimeout(() => {
    if (ws.readyState !== WebSocket.OPEN) {
        statusText.textContent = "Waking up server...";
    }
}, 3000);

ws.onopen = () => {
    statusText.textContent = "Online";
    statusBadge.classList.add("connected");
};

ws.onclose = () => {
    statusText.textContent = "Offline";
    statusBadge.classList.remove("connected");
    userCountLabel.textContent = "Active Users: 0";
};

ws.onmessage = (event) => {
    const data = JSON.parse(event.data);
    
    if (data.type === 'chat') {
        const msgDiv = document.createElement('div');

        msgDiv.textContent = `[${data.id}]: ${data.msg}`;
        chatBox.appendChild(msgDiv);
        chatBox.scrollTop = chatBox.scrollHeight; 
        return;
    }

        
    if (data.type === 'reset_ui') {
        const voteBtn = document.querySelector('.control-matrix button');
        if (voteBtn) {
            voteBtn.disabled = false;
            const total = data.nodes || 1;
            const current = data.votes || 0;
            
            voteBtn.textContent = `Vote to Reset (${current}/${total})`;
        }
        return;
    }


    if (data.type === 'cursor') {
        remoteCursors[data.id] = { x: data.x, y: data.y, lastUpdate: Date.now() };
        if (!isAnimatingWin) drawSystem(); 
        return;
    }

    if (data.type === 'win') {
        startWinAnimation();
        return;
    }

    if (data.type === 'init' || data.type === 'sync') {
        if (data.type === 'init') mySessionId = data.mySessionId;
        
        userCountLabel.textContent = `Active Users: ${data.nodes || 1}`;
        
        const voteBtn = document.querySelector('.control-matrix button');
        if (voteBtn) {
            const total = data.nodes || 1;
            const current = data.votes || 0;
            
            voteBtn.textContent = `Vote to Reset (${current}/${total})`;
            
            //maybe change button look if user has already voted

        }
    
        if (data.imageId && data.imageId !== currentImageId) {
            currentImageId = data.imageId;
            puzzleImg.src = `https://picsum.photos/seed/${currentImageId}/400/400`;
            
            isAnimatingWin = false; 
            rockets = [];
            particles = [];
        }
        
        data.pieces.forEach(serverPiece => {
            const localPiece = pieces.find(p => p.id === serverPiece.id);
            if (localPiece) {
                if ((!localPiece.snapped && serverPiece.snapped) || (localPiece.groupId !== serverPiece.groupId)) {
                    playClickSound();
                }

                if (serverPiece.lockedBy !== mySessionId) {
                    localPiece.x = serverPiece.x;
                    localPiece.y = serverPiece.y;
                }

                localPiece.lockedBy = serverPiece.lockedBy;
                localPiece.snapped = serverPiece.snapped;
                localPiece.groupId = serverPiece.groupId;
            } else {
                pieces.push(serverPiece);
            }
        });
        
        if (pieces.length !== data.pieces.length) {
            pieces = data.pieces;
        }
    }
    
    if (!isAnimatingWin) drawSystem();
};

function startWinAnimation() {
    isAnimatingWin = true;
    requestAnimationFrame(animateFireworks);
}

function createExplosion(x, y, color) {
    const numParticles = 40;
    for (let i = 0; i < numParticles; i++) {
        const angle = (Math.PI * 2 / numParticles) * i;
        const speed = 2 + Math.random() * 3;
        particles.push({
            x: x, y: y,
            vx: Math.cos(angle) * speed,
            vy: Math.sin(angle) * speed,
            color: color, alpha: 1,
            decay: 0.015 + Math.random() * 0.01
        });
    }
}


let rockets = [];
let particles = [];
const fwColors = ['#f33131', '#00e6b1', '#ec4899', '#07b6d4', '#5931b7'];

function animateFireworks() { 
    if (!isAnimatingWin) return;
    
    ctx.fillStyle = 'rgba(8, 9, 12, 0.15)';
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    if (Math.random() < 0.08) {
        rockets.push({
            x: 100 + Math.random() * (canvas.width - 200),
            y: canvas.height,
            targetY: 50 + Math.random() * (canvas.height / 2),
            vy: -6 - Math.random() * 4,
            color: fwColors[Math.floor(Math.random() * fwColors.length)]
        });
    }

    for (let i = rockets.length - 1; i >= 0; i--) {
        let r = rockets[i];
        r.y += r.vy;
        
        ctx.fillStyle = r.color;
        ctx.beginPath();
        ctx.arc(r.x, r.y, 3, 0, Math.PI * 2);
        ctx.fill();

        if (r.y <= r.targetY || r.vy >= 0) {
            createExplosion(r.x, r.y, r.color);
            rockets.splice(i, 1);
        }
    }

    for (let i = particles.length - 1; i >= 0; i--) {
        let p = particles[i];
        
        p.vy += 0.05; 
        p.vx *= 0.98;
        p.vy *= 0.98;
        
        p.x += p.vx;
        p.y += p.vy;
        p.alpha -= p.decay;

        if (p.alpha <= 0) {
            particles.splice(i, 1);
            continue;
        }

        ctx.globalAlpha = p.alpha;
        ctx.fillStyle = p.color;
        ctx.beginPath();
        ctx.arc(p.x, p.y, 2, 0, Math.PI * 2);
        ctx.fill();
        ctx.globalAlpha = 1.0; 
    }

    requestAnimationFrame(animateFireworks);
}

let audioCtx = null;

function playClickSound() {
    
    if (!audioCtx) {
        audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    }

    if (audioCtx.state === 'suspended') {
        audioCtx.resume();
    }

    const oscillator = audioCtx.createOscillator();
    const gainNode = audioCtx.createGain();

    oscillator.type = 'sine';

    oscillator.frequency.setValueAtTime(520, audioCtx.currentTime);
    oscillator.frequency.exponentialRampToValueAtTime(140, audioCtx.currentTime + 0.06);

    gainNode.gain.setValueAtTime(0.15, audioCtx.currentTime);
    gainNode.gain.exponentialRampToValueAtTime(0.001, audioCtx.currentTime + 0.06);
    
    oscillator.connect(gainNode);
    gainNode.connect(audioCtx.destination);
    
    oscillator.start();
    oscillator.stop(audioCtx.currentTime + 0.06);
    
}

function pieceCutter(context, x, y, top, right, bottom, left) {
    context.beginPath();
    context.moveTo(x, y);
    
    // 0 is flat side, -1 is indent, 1 is outward bump

    if (top === 1) {
        context.lineTo(x + tileSize * 0.35, y);
        context.bezierCurveTo(x + tileSize * 0.35, y - 15, x + tileSize *0.65, y - 15, x + tileSize * 0.65, y);
    } else if (top === -1) {
        context.lineTo(x + tileSize * 0.35, y);
        context.bezierCurveTo(x + tileSize * 0.35, y + 15, x + tileSize * 0.65, y + 15, x + tileSize * 0.65, y);
    }

    context.lineTo(x + tileSize, y);
    
    if (right === 1) {

        context.lineTo(x + tileSize, y + tileSize * 0.35);
        context.bezierCurveTo(x + tileSize + 15, y + tileSize * 0.35, x + tileSize + 15, y + tileSize *0.65, x + tileSize, y + tileSize * 0.65);
    } else if (right === -1) {
        context.lineTo(x + tileSize, y + tileSize * 0.35);
        context.bezierCurveTo(x + tileSize - 15, y + tileSize * 0.35, x + tileSize - 15, y + tileSize *0.65, x + tileSize, y + tileSize * 0.65);
    }

    context.lineTo(x + tileSize, y + tileSize);
    
    if (bottom === 1) {
        context.lineTo(x + tileSize * 0.65, y + tileSize);
        context.bezierCurveTo(x + tileSize * 0.65, y + tileSize + 15, x + tileSize * 0.35, y + tileSize + 15, x + tileSize * 0.35, y + tileSize);
    } else if (bottom === -1) {
        context.lineTo(x + tileSize * 0.65, y + tileSize);
        context.bezierCurveTo(x + tileSize * 0.65, y + tileSize - 15, x + tileSize * 0.35, y + tileSize - 15, x + tileSize * 0.35, y + tileSize);
    }

    context.lineTo(x, y + tileSize);
    
    if (left === 1) {
        context.lineTo(x, y + tileSize * 0.65);
        context.bezierCurveTo(x - 15, y + tileSize * 0.65, x - 15, y + tileSize * 0.35, x, y + tileSize * 0.35);
    } else if (left === -1) {
        context.lineTo(x, y + tileSize * 0.65);
        context.bezierCurveTo(x + 15, y + tileSize * 0.65, x + 15, y + tileSize * 0.35, x, y + tileSize * 0.35);
    }

    context.closePath();
}


function renderPiece(piece) {
    ctx.save();
    pieceCutter(ctx, piece.x, piece.y, piece.topEdge, piece.rightEdge, piece.bottomEdge, piece.leftEdge);
    ctx.clip();
    
    const pad = 20;
    const sourceX = (piece.col * tileSize) - pad;
    const sourceY = (piece.row * tileSize) - pad;
    const sourceSize = tileSize + (pad * 2);
    
    ctx.drawImage(puzzleImg, sourceX, sourceY, sourceSize, sourceSize, piece.x - pad, piece.y - pad, sourceSize, sourceSize);
    ctx.restore();

    ctx.save();
    pieceCutter(ctx, piece.x, piece.y, piece.topEdge, piece.rightEdge, piece.bottomEdge, piece.leftEdge);
    
    if (piece.snapped) {
        ctx.strokeStyle = 'rgba(11, 179, 123, 0.4)'; 
        ctx.lineWidth = 1;
    } else if (piece.lockedBy != null) {
        ctx.strokeStyle = piece.lockedBy === mySessionId ? '#205de0' : '#cf0d0d';
        ctx.lineWidth = 2;
    } else {
        ctx.strokeStyle = '#2a2d3c';
        ctx.lineWidth = 1;
    }
    ctx.stroke();
    ctx.restore();
}




function drawSystem() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    
    ctx.strokeStyle = '#e5e7eb'; 
    ctx.lineWidth = 1;
    for (let row = 0; row <= rows; row++) {
        ctx.beginPath();
        ctx.moveTo(gridStartX, gridStartY + row * tileSize);
        ctx.lineTo(gridStartX + cols * tileSize, gridStartY + row * tileSize);
        ctx.stroke();
    }
    for (let col = 0; col <= cols; col++) {
        ctx.beginPath();
        ctx.moveTo(gridStartX + col * tileSize, gridStartY);
        ctx.lineTo(gridStartX + col * tileSize, gridStartY + rows * tileSize);
        ctx.stroke();
    }


    pieces.forEach(piece => {
        if (selectedPiece && piece.groupId === selectedPiece.groupId) return;
        renderPiece(piece);
    });

  
    if (selectedPiece) {
        pieces.forEach(piece => {
            if (piece.groupId === selectedPiece.groupId) {
                renderPiece(piece);
            }
        });
    }
 

    const now = Date.now();
    for (const [id, cursor] of Object.entries(remoteCursors)) {
        if (now - cursor.lastUpdate > 5000) {
            delete remoteCursors[id];
            continue;
        }
        
        ctx.save();
        ctx.fillStyle = '#10b981'; 
        ctx.shadowColor = '#10b981';
        ctx.shadowBlur = 8; 
        
        ctx.beginPath();
        ctx.arc(cursor.x, cursor.y, 6, 0, Math.PI * 2); 
        ctx.fill();
        ctx.restore();
    }
}

canvas.addEventListener('mousedown', (e) => {
    if (isAnimatingWin) return; 
    
    const rect = canvas.getBoundingClientRect();
    const mouseX = e.clientX - rect.left;
    const mouseY = e.clientY - rect.top;

    for (let i = pieces.length - 1; i >= 0; i--) {
        const p = pieces[i];
        if (p.snapped) continue;

        ctx.save();
        pieceCutter(ctx, p.x, p.y, p.topEdge, p.rightEdge, p.bottomEdge, p.leftEdge);
        if (ctx.isPointInPath(mouseX, mouseY)) {
            const groupLocked = pieces.some(k => k.groupId === p.groupId && k.lockedBy != null && k.lockedBy !== mySessionId);
            
            //console.log(`clicked piece ${p.id} group: ${p.groupId}`);

            if (p.lockedBy == null && !groupLocked) {
                selectedPiece = p;
                dragOffsetX = mouseX - p.x;
                dragOffsetY = mouseY - p.y;
                ws.send(JSON.stringify({ action: 'grab', pieceId: p.id }));
                ctx.restore();
                break;
            }
        }
        ctx.restore();
    }
}); 

chatInput.addEventListener('keypress', (e) => {
    if (e.key === 'Enter' && chatInput.value.trim() !== '') {
        ws.send(JSON.stringify({ action: 'chat', msg: chatInput.value }));
        chatInput.value = '';
    }
});


canvas.addEventListener('mousemove', (e) => {
    if (isAnimatingWin) return;
    
    const rect = canvas.getBoundingClientRect();
    const mouseX = e.clientX - rect.left;
    const mouseY = e.clientY - rect.top;

    if (ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ action: 'cursor', x: mouseX, y: mouseY }));
    }

    if (!selectedPiece) return; 

    const targetNewX = mouseX - dragOffsetX;
    const targetNewY = mouseY - dragOffsetY;
    const deltaX = targetNewX - selectedPiece.x;
    const deltaY = targetNewY - selectedPiece.y;

    const groupId = selectedPiece.groupId;
    pieces.forEach(p => {
        if (p.groupId === groupId) {
            p.x += deltaX;
            p.y += deltaY;
        }
    });

    ws.send(JSON.stringify({
        action: 'move',
        pieceId: selectedPiece.id,
        x: selectedPiece.x,
        y: selectedPiece.y
    }));
    drawSystem();
});

canvas.addEventListener('mouseleave', () => {
    if (ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ action: 'cursor', x: -1000, y: -1000 }));
    }
});

canvas.addEventListener('mouseup', () => {
    if (!selectedPiece || isAnimatingWin) return;

    ws.send(JSON.stringify({ 
        action: 'drop', 
        pieceId: selectedPiece.id,
        x: selectedPiece.x,
        y: selectedPiece.y
    }));
    selectedPiece = null;
    drawSystem();
});

function sendVote() {
    if (ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ action: 'vote' }));
        document.querySelector('.control-matrix button').textContent = "Voted!";
        document.querySelector('.control-matrix button').disabled = true;
    }
}