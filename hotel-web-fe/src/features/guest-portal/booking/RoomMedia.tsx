// Photo carousel for a room-type card on the Choose step.
import React, { useRef, useState } from 'react';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import CloseIcon from '@mui/icons-material/Close';
import FullscreenIcon from '@mui/icons-material/Fullscreen';
import HotelIcon from '@mui/icons-material/Hotel';
import { Box, Dialog, IconButton } from '@mui/material';
import { useTranslation } from '../../../i18n';
import type { GuestBookingOffer } from './types';
import { offerPhotos } from './roomMediaSources';

export const ROOM_MEDIA_HEIGHT = 200;
const FALLBACK = 'linear-gradient(135deg, var(--hotel-surface-raised) 0%, var(--hotel-surface-sunken) 55%, var(--hotel-primary) 160%)';

export function RoomMedia({ offer }: { offer: GuestBookingOffer }) {
  const { t } = useTranslation('guestPortal');
  const { urls, bundled } = offerPhotos(offer);
  const [failed, setFailed] = useState<ReadonlySet<string>>(new Set());
  const working = urls.filter((u) => !failed.has(u));
  // every uploaded photo broken: the bundled landing photo, then the placeholder
  const photos = working.length > 0 ? working : bundled && !failed.has(bundled) ? [bundled] : [];
  const [index, setIndex] = useState(0);
  const [lightbox, setLightbox] = useState(false);
  const trackRef = useRef<HTMLDivElement | null>(null);
  const current = Math.min(index, Math.max(0, photos.length - 1));

  const go = (next: number) => {
    const n = (next + photos.length) % photos.length;
    setIndex(n);
    const track = trackRef.current;
    track?.scrollTo?.({ left: n * track.clientWidth, behavior: 'smooth' });
  };
  const navSx = { position: 'absolute', top: '50%', transform: 'translateY(-50%)', width: 44, height: 44, bgcolor: 'rgba(6,17,14,0.55)', color: '#fff', '&:hover': { bgcolor: 'rgba(6,17,14,0.75)' } } as const;

  return (
    <Box sx={{ position: 'relative', height: ROOM_MEDIA_HEIGHT, overflow: 'hidden', bgcolor: 'var(--hotel-surface-raised)' }} data-testid="room-media">
      {photos.length > 0 ? (
        <>
          <Box
            ref={trackRef}
            onScroll={(e) => { const el = e.currentTarget; if (el.clientWidth) setIndex(Math.round(el.scrollLeft / el.clientWidth)); }}
            sx={{ display: 'flex', height: '100%', overflowX: 'auto', scrollSnapType: 'x mandatory', scrollbarWidth: 'none', '&::-webkit-scrollbar': { display: 'none' } }}
          >
            {photos.map((src, i) => (
              <Box key={src} component="img" src={src} loading="lazy" alt={t('book.roomImageAlt', { name: offer.room_type_name }) + (photos.length > 1 ? ` (${i + 1}/${photos.length})` : '')}
                onError={() => setFailed((prev) => new Set(prev).add(src))}
                onClick={() => setLightbox(true)}
                sx={{ flex: '0 0 100%', width: '100%', height: '100%', objectFit: 'cover', scrollSnapAlign: 'start', display: 'block', cursor: 'zoom-in' }} />
            ))}
          </Box>
          <IconButton aria-label={t('book.media.enlarge')} onClick={() => setLightbox(true)} sx={{ ...navSx, top: 8, right: 8, transform: 'none' }}><FullscreenIcon /></IconButton>
          {photos.length > 1 && (
            <>
              <IconButton aria-label={t('book.media.previous')} onClick={() => go(current - 1)} sx={{ ...navSx, left: 6 }}><ChevronLeftIcon /></IconButton>
              <IconButton aria-label={t('book.media.next')} onClick={() => go(current + 1)} sx={{ ...navSx, right: 6 }}><ChevronRightIcon /></IconButton>
              <Box aria-hidden="true" sx={{ position: 'absolute', bottom: 10, left: 0, right: 0, display: 'flex', justifyContent: 'center', gap: 0.75 }}>
                {photos.map((src, i) => <Box key={src} sx={{ width: 7, height: 7, borderRadius: '50%', bgcolor: i === current ? 'primary.main' : 'rgba(255,255,255,0.6)' }} />)}
              </Box>
            </>
          )}
        </>
      ) : (
        <Box aria-hidden="true" sx={{ height: '100%', background: FALLBACK, display: 'grid', placeItems: 'center', color: 'var(--hotel-text-secondary)' }}><HotelIcon sx={{ fontSize: 48 }} /></Box>
      )}
      {photos.length > 0 && (
        <Dialog
          fullScreen open={lightbox} onClose={() => setLightbox(false)}
          aria-label={offer.room_type_name}
          onKeyDown={(e) => {
            if (photos.length < 2) return;
            if (e.key === 'ArrowLeft') go(current - 1);
            if (e.key === 'ArrowRight') go(current + 1);
          }}
          slotProps={{ paper: { sx: { bgcolor: '#06110e', color: '#fff' } } }}
        >
          <Box sx={{ position: 'relative', height: '100%', display: 'grid', placeItems: 'center', p: { xs: 1, sm: 4 } }}>
            <Box component="img" src={photos[current]} data-testid="room-lightbox-image"
              alt={t('book.roomImageAlt', { name: offer.room_type_name }) + (photos.length > 1 ? ` (${current + 1}/${photos.length})` : '')}
              sx={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain', display: 'block' }} />
            <IconButton aria-label={t('book.media.close')} onClick={() => setLightbox(false)} sx={{ ...navSx, top: 12, right: 12, transform: 'none' }}><CloseIcon /></IconButton>
            {photos.length > 1 && (
              <>
                <IconButton aria-label={t('book.media.previous')} onClick={() => go(current - 1)} sx={{ ...navSx, left: 12 }}><ChevronLeftIcon /></IconButton>
                <IconButton aria-label={t('book.media.next')} onClick={() => go(current + 1)} sx={{ ...navSx, right: 12 }}><ChevronRightIcon /></IconButton>
                <Box sx={{ position: 'absolute', bottom: 16, left: 0, right: 0, textAlign: 'center', fontSize: 14, opacity: 0.85 }}>{current + 1} / {photos.length}</Box>
              </>
            )}
          </Box>
        </Dialog>
      )}
    </Box>
  );
}
