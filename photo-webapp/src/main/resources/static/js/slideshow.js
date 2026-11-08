/* Slideshow behaviour shared by index.html and index_mobile.html. body.html is injected after page
 * load, so element-specific setup waits for it (whenBodyReady) and events are bound on document. */

const AUTOPLAY_INTERVAL_MS = 5000;
let autoplayOn = false;
let autoplayTimer = null;
let searchEnabled = false;
let currentKey = '';

function setLoading(loading) {
    $('#loaderForward, #loaderBackward').prop('disabled', loading);
    $('#spinner').css('visibility', loading ? 'visible' : 'hidden');
}

function loadImage(direction) {
    clearTimeout(autoplayTimer); // the next auto-advance is scheduled once this photo has loaded
    setLoading(true);
    let url = 'image?height=' + window.screen.height + '&width=' + window.screen.width;
    const year = $('#year').val();
    if (year !== undefined) {
        url += '&year=' + year;
    }
    const quality = $('#quality').val();
    if (quality !== undefined) {
        url += '&quality=' + quality;
    }
    if (direction !== undefined) {
        url += '&direction=' + direction;
    }
    $.get(url, function (data, status, xhr) {
        if (data.includes('login')) {
            location.href = 'login';
            return;
        }
        $('#img').attr('src', 'data:image/jpeg;base64, ' + data);
        showPhotoInfo(xhr.getResponseHeader('X-Photo-Key'));
        setLoading(false);
        scheduleAutoplay();
    }).fail(function () {
        setLoading(false);
        stopAutoplay(); // e.g. no more photos for this year
    });
}

// the server sends the photo's S3 key (URL-encoded), e.g. "2019/IMG_123.jpg"
function showPhotoInfo(encodedKey) {
    currentKey = encodedKey ? decodeURIComponent(encodedKey) : '';
    $('#photoYear').text(currentKey.includes('/') ? currentKey.substring(0, currentKey.indexOf('/')) : '');
    $('#similarLink').attr('href', 'search?similar=' + encodeURIComponent(currentKey))
        .toggle(searchEnabled && currentKey !== '');
}

// same as clicking an on-photo arrow; ignored while a photo is still loading
function navigate(direction) {
    const arrow = direction === 'NEXT' ? '#loaderForward' : '#loaderBackward';
    if ($(arrow).length && !$(arrow).prop('disabled')) {
        loadImage(direction);
    }
}

/* ---------- auto-play ---------- */

function scheduleAutoplay() {
    clearTimeout(autoplayTimer);
    if (autoplayOn) {
        autoplayTimer = setTimeout(() => navigate('NEXT'), AUTOPLAY_INTERVAL_MS);
    }
}

function toggleAutoplay() {
    autoplayOn ? stopAutoplay() : startAutoplay();
}

function startAutoplay() {
    autoplayOn = true;
    $('#playButton').html('&#10074;&#10074;').attr('title', 'Пауза (пробіл)');
    scheduleAutoplay();
}

function stopAutoplay() {
    autoplayOn = false;
    clearTimeout(autoplayTimer);
    $('#playButton').html('&#9654;').attr('title', 'Автоматичний показ (пробіл)');
}

/* ---------- full screen ---------- */

function toggleFullscreen() {
    const stage = document.getElementById('img_holder');
    if (document.fullscreenElement) {
        document.exitFullscreen();
    } else if (stage && stage.requestFullscreen) {
        stage.requestFullscreen();
    }
}

/* ---------- fit the photo stage to the window ---------- */

function fitStage() {
    const stage = $('#img_holder');
    if (!stage.length || document.fullscreenElement) return;
    const controls = $('.controls').outerHeight(true) || 0;
    const top = stage.offset().top;
    stage.css('height', Math.max(240, window.innerHeight - top - controls) + 'px');
}

function whenBodyReady(callback) {
    $('#img_holder').length ? callback() : setTimeout(() => whenBodyReady(callback), 50);
}

/* ---------- search features ---------- */

// "Пошук" and "Схожі" appear only when the server has search enabled (clip.search.enabled)
function showSearchLinkIfEnabled() {
    $.ajax({url: 'search', method: 'HEAD'}).done(function () {
        searchEnabled = true;
        whenBodyReady(() => {
            $('#searchLink').show();
            $('#similarLink').toggle(currentKey !== '');
            fitStage();
        });
    });
}

/* ---------- keyboard and touch ---------- */

$(document).on('keydown', function (e) {
    if ($(e.target).is('select, input, textarea')) return; // arrows change a dropdown's value
    if (e.key === 'ArrowRight') { e.preventDefault(); navigate('NEXT'); }
    if (e.key === 'ArrowLeft') { e.preventDefault(); navigate('PREVIOUS'); }
    if (e.key === ' ') { e.preventDefault(); toggleAutoplay(); }
    if (e.key === 'f' || e.key === 'F') { toggleFullscreen(); }
});

let touchStartX = null, touchStartY = null;
$(document).on('touchstart', '#img_holder', function (e) {
    touchStartX = e.originalEvent.touches[0].clientX;
    touchStartY = e.originalEvent.touches[0].clientY;
});
$(document).on('touchend', '#img_holder', function (e) {
    if (touchStartX === null) return;
    const dx = e.originalEvent.changedTouches[0].clientX - touchStartX;
    const dy = e.originalEvent.changedTouches[0].clientY - touchStartY;
    touchStartX = null;
    if (Math.abs(dx) > 50 && Math.abs(dx) > 2 * Math.abs(dy)) { // mostly horizontal, not a scroll
        navigate(dx < 0 ? 'NEXT' : 'PREVIOUS');
    }
});

$(window).on('resize', fitStage);
$(document).on('fullscreenchange', fitStage);

function startSlideshow() {
    whenBodyReady(function () {
        fitStage();
        loadImage();
    });
    showSearchLinkIfEnabled();
}
