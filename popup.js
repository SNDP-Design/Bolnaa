/**
 * CopyMe - Quick Link Clipboard
 * Manifest V3 Chrome Extension Logic
 */

(function () {
  'use strict';

  // --- State & Config ---
  const STORAGE_KEY_LINKS = 'copyme_links';
  const STORAGE_KEY_SETTINGS = 'copyme_settings';

  let links = [];
  let settings = {
    copyFormat: 'url', // 'url', 'markdown', 'title-url', 'html'
  };
  let selectedCategory = 'all';
  let searchQuery = '';

  const SAMPLE_LINKS = [
    {
      id: 'sample_1',
      title: 'GitHub',
      url: 'https://github.com',
      category: 'Dev',
      pinned: true,
      copyCount: 0,
      createdAt: Date.now() - 3000,
    },
    {
      id: 'sample_2',
      title: 'Google Search',
      url: 'https://google.com',
      category: 'General',
      pinned: false,
      copyCount: 0,
      createdAt: Date.now() - 2000,
    },
    {
      id: 'sample_3',
      title: 'ChatGPT',
      url: 'https://chatgpt.com',
      category: 'AI',
      pinned: false,
      copyCount: 0,
      createdAt: Date.now() - 1000,
    }
  ];

  // --- DOM Elements ---
  const elements = {
    linksContainer: document.getElementById('linksContainer'),
    emptyState: document.getElementById('emptyState'),
    linkCountBadge: document.getElementById('linkCountBadge'),
    searchInput: document.getElementById('searchInput'),
    clearSearchBtn: document.getElementById('clearSearchBtn'),
    categoriesBar: document.getElementById('categoriesBar'),
    categoriesDatalist: document.getElementById('categoriesDatalist'),
    copyFormatIndicator: document.getElementById('copyFormatIndicator'),
    
    // Action Buttons
    addCurrentTabBtn: document.getElementById('addCurrentTabBtn'),
    openAddModalBtn: document.getElementById('openAddModalBtn'),
    menuBtn: document.getElementById('menuBtn'),
    menuDropdown: document.getElementById('menuDropdown'),
    emptyAddTabBtn: document.getElementById('emptyAddTabBtn'),
    addSampleLinksBtn: document.getElementById('addSampleLinksBtn'),
    
    // Modal Elements
    linkModal: document.getElementById('linkModal'),
    modalTitle: document.getElementById('modalTitle'),
    linkForm: document.getElementById('linkForm'),
    editLinkId: document.getElementById('editLinkId'),
    linkUrl: document.getElementById('linkUrl'),
    linkTitle: document.getElementById('linkTitle'),
    linkCategory: document.getElementById('linkCategory'),
    linkPinned: document.getElementById('linkPinned'),
    closeModalBtn: document.getElementById('closeModalBtn'),
    cancelModalBtn: document.getElementById('cancelModalBtn'),
    
    // Shortcuts Modal
    shortcutsModal: document.getElementById('shortcutsModal'),
    shortcutsBtn: document.getElementById('shortcutsBtn'),
    closeShortcutsBtn: document.getElementById('closeShortcutsBtn'),
    doneShortcutsBtn: document.getElementById('doneShortcutsBtn'),
    
    // Menu & Export/Import
    formatOptions: document.getElementById('formatOptions'),
    exportBtn: document.getElementById('exportBtn'),
    importBtn: document.getElementById('importBtn'),
    importFileInput: document.getElementById('importFileInput'),
    clearAllBtn: document.getElementById('clearAllBtn'),
    
    // Toast
    toast: document.getElementById('toast'),
    toastIcon: document.getElementById('toastIcon'),
    toastMessage: document.getElementById('toastMessage')
  };

  let toastTimeout = null;

  // --- Storage Helper (Sync with Local Fallback) ---
  const storage = {
    get: function (keys) {
      return new Promise((resolve) => {
        if (chrome && chrome.storage && chrome.storage.sync) {
          chrome.storage.sync.get(keys, (res) => {
            if (chrome.runtime.lastError) {
              chrome.storage.local.get(keys, resolve);
            } else {
              resolve(res);
            }
          });
        } else if (chrome && chrome.storage && chrome.storage.local) {
          chrome.storage.local.get(keys, resolve);
        } else {
          // LocalStorage fallback for testing outside extension environment
          const res = {};
          if (Array.isArray(keys)) {
            keys.forEach((k) => {
              const item = localStorage.getItem(k);
              if (item) res[k] = JSON.parse(item);
            });
          } else if (typeof keys === 'string') {
            const item = localStorage.getItem(keys);
            if (item) res[keys] = JSON.parse(item);
          }
          resolve(res);
        }
      });
    },

    set: function (items) {
      return new Promise((resolve) => {
        if (chrome && chrome.storage && chrome.storage.sync) {
          chrome.storage.sync.set(items, () => {
            if (chrome.runtime.lastError) {
              chrome.storage.local.set(items, resolve);
            } else {
              resolve();
            }
          });
        } else if (chrome && chrome.storage && chrome.storage.local) {
          chrome.storage.local.set(items, resolve);
        } else {
          // LocalStorage fallback
          Object.keys(items).forEach((k) => {
            localStorage.setItem(k, JSON.stringify(items[k]));
          });
          resolve();
        }
      });
    }
  };

  // --- Initialize App ---
  async function init() {
    await loadData();
    setupEventListeners();
    renderCategories();
    renderLinks();
    updateFormatIndicator();
  }

  // --- Load Data from Storage ---
  async function loadData() {
    const data = await storage.get([STORAGE_KEY_LINKS, STORAGE_KEY_SETTINGS]);
    
    if (data[STORAGE_KEY_LINKS] && Array.isArray(data[STORAGE_KEY_LINKS])) {
      links = data[STORAGE_KEY_LINKS];
    } else {
      // First time launch: use sample links
      links = [...SAMPLE_LINKS];
      await storage.set({ [STORAGE_KEY_LINKS]: links });
    }

    if (data[STORAGE_KEY_SETTINGS]) {
      settings = { ...settings, ...data[STORAGE_KEY_SETTINGS] };
    }
  }

  // --- Save Links to Storage ---
  async function saveLinks() {
    await storage.set({ [STORAGE_KEY_LINKS]: links });
    renderCategories();
    renderLinks();
  }

  // --- Save Settings ---
  async function saveSettings() {
    await storage.set({ [STORAGE_KEY_SETTINGS]: settings });
    updateFormatIndicator();
  }

  // --- Format Copied Text ---
  function formatLinkContent(item, format = settings.copyFormat) {
    const title = item.title || item.url;
    const url = item.url;

    switch (format) {
      case 'markdown':
        return `[${title}](${url})`;
      case 'title-url':
        return `${title} - ${url}`;
      case 'html':
        return `<a href="${url}">${escapeHtml(title)}</a>`;
      case 'url':
      default:
        return url;
    }
  }

  // --- Copy to Clipboard Core ---
  async function copyToClipboard(text, item = null, cardElement = null) {
    let success = false;

    if (navigator.clipboard && navigator.clipboard.writeText) {
      try {
        await navigator.clipboard.writeText(text);
        success = true;
      } catch (err) {
        console.warn('navigator.clipboard failed, falling back:', err);
      }
    }

    if (!success) {
      // Fallback
      const textarea = document.createElement('textarea');
      textarea.value = text;
      textarea.style.position = 'fixed';
      textarea.style.opacity = '0';
      document.body.appendChild(textarea);
      textarea.select();
      try {
        document.execCommand('copy');
        success = true;
      } catch (err) {
        console.error('Copy fallback failed:', err);
      }
      document.body.removeChild(textarea);
    }

    if (success) {
      if (item) {
        item.copyCount = (item.copyCount || 0) + 1;
        storage.set({ [STORAGE_KEY_LINKS]: links });
      }

      // Visual feedback on card
      if (cardElement) {
        cardElement.classList.add('copied-pulse');
        const badge = document.createElement('div');
        badge.className = 'copy-badge-float';
        badge.innerHTML = `
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
            <polyline points="20 6 9 17 4 12"></polyline>
          </svg>
          Copied!
        `;
        cardElement.appendChild(badge);
        setTimeout(() => {
          cardElement.classList.remove('copied-pulse');
          badge.remove();
        }, 800);
      }

      showToast('Copied to clipboard! 📋', '✨');
    } else {
      showToast('Failed to copy', '⚠️');
    }
  }

  // --- Toast Notifications ---
  function showToast(message, icon = '✨') {
    if (toastTimeout) clearTimeout(toastTimeout);
    elements.toastMessage.textContent = message;
    elements.toastIcon.textContent = icon;
    elements.toast.classList.remove('hidden');

    toastTimeout = setTimeout(() => {
      elements.toast.classList.add('hidden');
    }, 1800);
  }

  // --- Extract Clean Domain / Hostname ---
  function getHostname(urlStr) {
    try {
      const parsed = new URL(urlStr);
      return parsed.hostname;
    } catch {
      return '';
    }
  }

  // --- Favicon URL Helper ---
  function getFaviconUrl(urlStr) {
    const domain = getHostname(urlStr);
    if (!domain) return null;
    return `https://www.google.com/s2/favicons?domain=${encodeURIComponent(domain)}&sz=32`;
  }

  // --- Clean Tab Title ---
  function cleanTabTitle(title, url) {
    if (!title) return '';
    let cleaned = title.trim();
    // Strip common suffixes like "- Google Search", " - YouTube", etc.
    cleaned = cleaned.replace(/\s*[-–—|]\s*(Google Search|YouTube|GitHub|Twitter|X|LinkedIn|Reddit|Wikipedia)$/i, '');
    return cleaned || title;
  }

  // --- URL Normalizer ---
  function normalizeUrl(url) {
    let trimmed = url.trim();
    if (!trimmed) return '';
    if (!/^https?:\/\//i.test(trimmed) && !trimmed.startsWith('chrome://') && !trimmed.startsWith('mailto:') && !trimmed.startsWith('file://')) {
      if (trimmed.includes('.') && !trimmed.includes(' ')) {
        return 'https://' + trimmed;
      }
    }
    return trimmed;
  }

  // --- Auto-generate Title from URL ---
  function generateTitleFromUrl(urlStr) {
    try {
      const url = new URL(urlStr);
      let host = url.hostname.replace(/^www\./i, '');
      const parts = host.split('.');
      if (parts.length >= 2) {
        return parts[0].charAt(0).toUpperCase() + parts[0].slice(1);
      }
      return host;
    } catch {
      return urlStr.slice(0, 30);
    }
  }

  // --- Get Filtered & Sorted Links ---
  function getFilteredLinks() {
    return links
      .filter((link) => {
        // Category filter
        if (selectedCategory !== 'all') {
          const cat = (link.category || '').toLowerCase();
          if (cat !== selectedCategory.toLowerCase()) return false;
        }

        // Search filter
        if (searchQuery) {
          const q = searchQuery.toLowerCase();
          const matchTitle = (link.title || '').toLowerCase().includes(q);
          const matchUrl = (link.url || '').toLowerCase().includes(q);
          const matchCat = (link.category || '').toLowerCase().includes(q);
          if (!matchTitle && !matchUrl && !matchCat) return false;
        }

        return true;
      })
      .sort((a, b) => {
        // Pinned links first
        if (a.pinned && !b.pinned) return -1;
        if (!a.pinned && b.pinned) return 1;
        // Then by creation date descending
        return (b.createdAt || 0) - (a.createdAt || 0);
      });
  }

  // --- Render Categories Bar ---
  function renderCategories() {
    const categoriesSet = new Set();
    links.forEach((l) => {
      if (l.category && l.category.trim()) {
        categoriesSet.add(l.category.trim());
      }
    });

    const categories = Array.from(categoriesSet).sort();

    // Populate Datalist for form input
    elements.categoriesDatalist.innerHTML = categories
      .map((c) => `<option value="${escapeHtml(c)}"></option>`)
      .join('');

    if (categories.length === 0) {
      elements.categoriesBar.innerHTML = '';
      elements.categoriesBar.classList.add('hidden');
      return;
    }

    elements.categoriesBar.classList.remove('hidden');
    let pillsHtml = `
      <button class="cat-pill ${selectedCategory === 'all' ? 'active' : ''}" data-category="all">
        All (${links.length})
      </button>
    `;

    categories.forEach((cat) => {
      const count = links.filter((l) => (l.category || '').toLowerCase() === cat.toLowerCase()).length;
      const isActive = selectedCategory.toLowerCase() === cat.toLowerCase();
      pillsHtml += `
        <button class="cat-pill ${isActive ? 'active' : ''}" data-category="${escapeHtml(cat)}">
          ${escapeHtml(cat)} (${count})
        </button>
      `;
    });

    elements.categoriesBar.innerHTML = pillsHtml;

    // Attach pill click events
    elements.categoriesBar.querySelectorAll('.cat-pill').forEach((pill) => {
      pill.addEventListener('click', () => {
        selectedCategory = pill.dataset.category;
        renderCategories();
        renderLinks();
      });
    });
  }

  // --- Render Links List ---
  function renderLinks() {
    const filtered = getFilteredLinks();
    elements.linkCountBadge.textContent = `${links.length} link${links.length === 1 ? '' : 's'}`;

    if (filtered.length === 0) {
      elements.linksContainer.innerHTML = '';
      elements.emptyState.classList.remove('hidden');

      if (searchQuery || selectedCategory !== 'all') {
        document.getElementById('emptyTitle').textContent = 'No matching links found';
        document.getElementById('emptyDesc').textContent = 'Try adjusting your search query or category filter.';
      } else {
        document.getElementById('emptyTitle').textContent = 'No links saved yet';
        document.getElementById('emptyDesc').textContent = 'Save your favorite links here to copy them anytime with just one click.';
      }
      return;
    }

    elements.emptyState.classList.add('hidden');
    elements.linksContainer.innerHTML = '';

    filtered.forEach((item, index) => {
      const card = createLinkCard(item, index);
      elements.linksContainer.appendChild(card);
    });
  }

  // --- Create Single Link Card Element ---
  function createLinkCard(item, index) {
    const card = document.createElement('div');
    card.className = 'link-card';
    card.setAttribute('role', 'listitem');
    card.setAttribute('tabindex', '0');
    card.dataset.id = item.id;
    card.dataset.index = (index + 1).toString();

    const formattedContent = formatLinkContent(item);
    card.title = `Click to copy: ${formattedContent}`;

    // Index badge (1-9 keyboard shortcut)
    const indexBadgeHtml = index < 9
      ? `<div class="card-index-badge" title="Press '${index + 1}' to copy">${index + 1}</div>`
      : `<div class="card-index-badge" style="opacity: 0.4">•</div>`;

    // Favicon
    const faviconUrl = getFaviconUrl(item.url);
    const faviconHtml = faviconUrl
      ? `<img src="${faviconUrl}" alt="" loading="lazy" onerror="this.style.display='none'; this.nextElementSibling.style.display='block';" />
         <svg style="display:none;" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
           <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"></path>
           <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"></path>
         </svg>`
      : `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
           <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"></path>
           <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"></path>
         </svg>`;

    const titleText = item.title || item.url;
    const highlightedTitle = highlightMatch(titleText, searchQuery);
    const highlightedUrl = highlightMatch(item.url, searchQuery);

    const pinBadge = item.pinned ? `<span class="pin-icon" title="Pinned link">⭐</span>` : '';
    const tagBadge = item.category ? `<span class="card-tag">${escapeHtml(item.category)}</span>` : '';

    card.innerHTML = `
      <div class="card-main">
        ${indexBadgeHtml}
        <div class="card-favicon">
          ${faviconHtml}
        </div>
        <div class="card-info">
          <div class="card-title-row">
            ${pinBadge}
            <span class="card-title">${highlightedTitle}</span>
            ${tagBadge}
          </div>
          <span class="card-url">${highlightedUrl}</span>
        </div>
      </div>

      <div class="card-actions">
        <button class="action-btn copy-btn" title="Copy to clipboard" data-action="copy">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
            <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
          </svg>
        </button>
        <button class="action-btn open-btn" title="Open in new tab" data-action="open">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"></path>
            <polyline points="15 3 21 3 21 9"></polyline>
            <line x1="10" y1="14" x2="21" y2="3"></line>
          </svg>
        </button>
        <button class="action-btn pin-toggle-btn" title="${item.pinned ? 'Unpin' : 'Pin to top'}" data-action="pin">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="${item.pinned ? 'currentColor' : 'none'}" stroke="currentColor" stroke-width="2">
            <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon>
          </svg>
        </button>
        <button class="action-btn edit-btn" title="Edit link" data-action="edit">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 20h9"></path>
            <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"></path>
          </svg>
        </button>
        <button class="action-btn delete-btn" title="Delete link" data-action="delete">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>
      </div>
    `;

    // Click handler for card
    card.addEventListener('click', (e) => {
      const actionBtn = e.target.closest('.action-btn');
      if (actionBtn) {
        const action = actionBtn.dataset.action;
        e.stopPropagation();
        handleCardAction(action, item, card);
        return;
      }
      // Clicking anywhere on card copies content
      copyToClipboard(formatLinkContent(item), item, card);
    });

    // Enter / Space key triggers copy
    card.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        copyToClipboard(formatLinkContent(item), item, card);
      }
    });

    return card;
  }

  // --- Handle Action Buttons on Card ---
  function handleCardAction(action, item, cardElement) {
    switch (action) {
      case 'copy':
        copyToClipboard(formatLinkContent(item), item, cardElement);
        break;
      case 'open':
        if (chrome && chrome.tabs && chrome.tabs.create) {
          chrome.tabs.create({ url: item.url });
        } else {
          window.open(item.url, '_blank');
        }
        break;
      case 'pin':
        item.pinned = !item.pinned;
        saveLinks();
        showToast(item.pinned ? 'Pinned to top ⭐' : 'Unpinned', '📌');
        break;
      case 'edit':
        openEditModal(item);
        break;
      case 'delete':
        deleteLink(item.id);
        break;
    }
  }

  // --- Highlight Search Matches ---
  function highlightMatch(text, query) {
    if (!text) return '';
    if (!query) return escapeHtml(text);

    const escapedQuery = query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const regex = new RegExp(`(${escapedQuery})`, 'gi');
    const parts = text.split(regex);

    return parts
      .map((part) =>
        regex.test(part)
          ? `<mark style="background: rgba(99, 102, 241, 0.35); color: inherit; padding: 0 1px; border-radius: 2px;">${escapeHtml(part)}</mark>`
          : escapeHtml(part)
      )
      .join('');
  }

  // --- HTML Escape Helper ---
  function escapeHtml(str) {
    if (!str) return '';
    return str
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  // --- Add Active Tab ---
  async function addActiveTab() {
    if (!chrome || !chrome.tabs || !chrome.tabs.query) {
      showToast('Chrome Tabs API not available', '⚠️');
      return;
    }

    try {
      const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
      if (!tab || !tab.url) {
        showToast('No active tab found', '⚠️');
        return;
      }

      if (tab.url.startsWith('chrome://') || tab.url.startsWith('chrome-extension://')) {
        showToast('Cannot save browser internal pages', '⚠️');
        return;
      }

      const existing = links.find((l) => l.url === tab.url);
      if (existing) {
        showToast('Link already in CopyMe! 📌', 'ℹ️');
        return;
      }

      const newLink = {
        id: 'link_' + Date.now() + '_' + Math.random().toString(36).substr(2, 5),
        title: cleanTabTitle(tab.title, tab.url) || generateTitleFromUrl(tab.url),
        url: tab.url,
        category: '',
        pinned: false,
        copyCount: 0,
        createdAt: Date.now()
      };

      links.unshift(newLink);
      await saveLinks();
      showToast(`Saved tab "${newLink.title.slice(0, 20)}..."`, '🎉');
    } catch (err) {
      console.error('Error adding active tab:', err);
      showToast('Could not grab current tab', '⚠️');
    }
  }

  // --- Modal: Open Add / Edit ---
  function openAddModal() {
    elements.modalTitle.textContent = 'Add New Link';
    elements.editLinkId.value = '';
    elements.linkForm.reset();
    elements.linkModal.classList.remove('hidden');
    setTimeout(() => elements.linkUrl.focus(), 50);
  }

  function openEditModal(item) {
    elements.modalTitle.textContent = 'Edit Link';
    elements.editLinkId.value = item.id;
    elements.linkUrl.value = item.url;
    elements.linkTitle.value = item.title || '';
    elements.linkCategory.value = item.category || '';
    elements.linkPinned.checked = !!item.pinned;
    elements.linkModal.classList.remove('hidden');
    setTimeout(() => elements.linkTitle.focus(), 50);
  }

  function closeModal() {
    elements.linkModal.classList.add('hidden');
    elements.linkForm.reset();
  }

  // --- Form Submit: Save / Update Link ---
  async function handleFormSubmit(e) {
    e.preventDefault();
    const rawUrl = elements.linkUrl.value.trim();
    if (!rawUrl) return;

    const normalizedUrl = normalizeUrl(rawUrl);
    const title = elements.linkTitle.value.trim() || generateTitleFromUrl(normalizedUrl);
    const category = elements.linkCategory.value.trim();
    const pinned = elements.linkPinned.checked;
    const editId = elements.editLinkId.value;

    if (editId) {
      // Edit existing
      const index = links.findIndex((l) => l.id === editId);
      if (index !== -1) {
        links[index] = {
          ...links[index],
          url: normalizedUrl,
          title,
          category,
          pinned,
          updatedAt: Date.now()
        };
        showToast('Link updated! ✨');
      }
    } else {
      // New link
      const newLink = {
        id: 'link_' + Date.now() + '_' + Math.random().toString(36).substr(2, 5),
        url: normalizedUrl,
        title,
        category,
        pinned,
        copyCount: 0,
        createdAt: Date.now()
      };
      links.unshift(newLink);
      showToast('Link added! ✨');
    }

    closeModal();
    await saveLinks();
  }

  // --- Delete Single Link ---
  async function deleteLink(id) {
    links = links.filter((l) => l.id !== id);
    await saveLinks();
    showToast('Link deleted', '🗑️');
  }

  // --- Clear All Links ---
  async function clearAllLinks() {
    if (confirm('Are you sure you want to delete ALL saved links?')) {
      links = [];
      await saveLinks();
      elements.menuDropdown.classList.add('hidden');
      showToast('All links cleared', '🧹');
    }
  }

  // --- Export Links to JSON ---
  function exportLinks() {
    const dataStr = 'data:text/json;charset=utf-8,' + encodeURIComponent(JSON.stringify(links, null, 2));
    const downloadAnchor = document.createElement('a');
    downloadAnchor.setAttribute('href', dataStr);
    downloadAnchor.setAttribute('download', `copyme-backup-${new Date().toISOString().slice(0, 10)}.json`);
    document.body.appendChild(downloadAnchor);
    downloadAnchor.click();
    downloadAnchor.remove();
    elements.menuDropdown.classList.add('hidden');
    showToast('Exported backup file! 📁');
  }

  // --- Import Links from JSON ---
  function importLinks(e) {
    const file = e.target.files[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = async (event) => {
      try {
        const imported = JSON.parse(event.target.result);
        if (Array.isArray(imported)) {
          // Merge imported with existing links (avoiding duplicate URLs)
          const existingUrls = new Set(links.map((l) => l.url));
          let addedCount = 0;

          imported.forEach((item) => {
            if (item && item.url && !existingUrls.has(item.url)) {
              links.push({
                id: item.id || 'link_' + Date.now() + '_' + Math.random().toString(36).substr(2, 5),
                url: normalizeUrl(item.url),
                title: item.title || generateTitleFromUrl(item.url),
                category: item.category || '',
                pinned: !!item.pinned,
                copyCount: item.copyCount || 0,
                createdAt: item.createdAt || Date.now()
              });
              existingUrls.add(item.url);
              addedCount++;
            }
          });

          await saveLinks();
          showToast(`Imported ${addedCount} new link${addedCount === 1 ? '' : 's'}! 📥`);
        } else {
          showToast('Invalid backup file format', '⚠️');
        }
      } catch (err) {
        console.error('Import error:', err);
        showToast('Error reading backup file', '⚠️');
      }
      elements.importFileInput.value = '';
      elements.menuDropdown.classList.add('hidden');
    };
    reader.readAsText(file);
  }

  // --- Update Format Indicator & Button States ---
  function updateFormatIndicator() {
    const labels = {
      url: 'URL',
      markdown: 'Markdown',
      'title-url': 'Title + URL',
      html: 'HTML'
    };
    elements.copyFormatIndicator.textContent = `Format: ${labels[settings.copyFormat] || 'URL'}`;

    elements.formatOptions.querySelectorAll('.format-btn').forEach((btn) => {
      btn.classList.toggle('active', btn.dataset.format === settings.copyFormat);
    });
  }

  // --- Setup Event Listeners ---
  function setupEventListeners() {
    // Search input
    elements.searchInput.addEventListener('input', (e) => {
      searchQuery = e.target.value.trim();
      elements.clearSearchBtn.classList.toggle('hidden', !searchQuery);
      renderLinks();
    });

    elements.clearSearchBtn.addEventListener('click', () => {
      elements.searchInput.value = '';
      searchQuery = '';
      elements.clearSearchBtn.classList.add('hidden');
      elements.searchInput.focus();
      renderLinks();
    });

    // Quick Add Active Tab buttons
    elements.addCurrentTabBtn.addEventListener('click', addActiveTab);
    elements.emptyAddTabBtn.addEventListener('click', addActiveTab);

    // Add Sample Links
    elements.addSampleLinksBtn.addEventListener('click', async () => {
      links = [...SAMPLE_LINKS];
      await saveLinks();
      showToast('Sample links restored! 🌟');
    });

    // Modals
    elements.openAddModalBtn.addEventListener('click', openAddModal);
    elements.closeModalBtn.addEventListener('click', closeModal);
    elements.cancelModalBtn.addEventListener('click', closeModal);
    elements.linkForm.addEventListener('submit', handleFormSubmit);

    // Close modal on outside click
    elements.linkModal.addEventListener('click', (e) => {
      if (e.target === elements.linkModal) closeModal();
    });

    // Menu Dropdown
    elements.menuBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      elements.menuDropdown.classList.toggle('hidden');
    });

    document.addEventListener('click', (e) => {
      if (!elements.menuDropdown.contains(e.target) && e.target !== elements.menuBtn) {
        elements.menuDropdown.classList.add('hidden');
      }
    });

    // Format selection buttons
    elements.formatOptions.querySelectorAll('.format-btn').forEach((btn) => {
      btn.addEventListener('click', async (e) => {
        e.stopPropagation();
        settings.copyFormat = btn.dataset.format;
        await saveSettings();
        showToast(`Format set to ${btn.textContent}! ✨`);
      });
    });

    // Export / Import
    elements.exportBtn.addEventListener('click', exportLinks);
    elements.importBtn.addEventListener('click', () => elements.importFileInput.click());
    elements.importFileInput.addEventListener('change', importLinks);
    elements.clearAllBtn.addEventListener('click', clearAllLinks);

    // Shortcuts Modal
    elements.shortcutsBtn.addEventListener('click', () => {
      elements.menuDropdown.classList.add('hidden');
      elements.shortcutsModal.classList.remove('hidden');
    });
    elements.closeShortcutsBtn.addEventListener('click', () => {
      elements.shortcutsModal.classList.add('hidden');
    });
    elements.doneShortcutsBtn.addEventListener('click', () => {
      elements.shortcutsModal.classList.add('hidden');
    });
    elements.shortcutsModal.addEventListener('click', (e) => {
      if (e.target === elements.shortcutsModal) elements.shortcutsModal.classList.add('hidden');
    });

    // Global Keyboard Shortcuts
    document.addEventListener('keydown', (e) => {
      const activeEl = document.activeElement;
      const isTyping = activeEl && (activeEl.tagName === 'INPUT' || activeEl.tagName === 'TEXTAREA');

      // Escape key to close modals / search
      if (e.key === 'Escape') {
        if (!elements.linkModal.classList.contains('hidden')) {
          closeModal();
          return;
        }
        if (!elements.shortcutsModal.classList.contains('hidden')) {
          elements.shortcutsModal.classList.add('hidden');
          return;
        }
        if (!elements.menuDropdown.classList.contains('hidden')) {
          elements.menuDropdown.classList.add('hidden');
          return;
        }
        if (elements.searchInput.value) {
          elements.searchInput.value = '';
          searchQuery = '';
          elements.clearSearchBtn.classList.add('hidden');
          renderLinks();
          return;
        }
      }

      if (isTyping) return;

      // "/" key: Focus search
      if (e.key === '/' || (e.key === 'k' && (e.metaKey || e.ctrlKey))) {
        e.preventDefault();
        elements.searchInput.focus();
        elements.searchInput.select();
        return;
      }

      // "n" key: Open new link modal
      if (e.key === 'n' || e.key === 'N') {
        e.preventDefault();
        openAddModal();
        return;
      }

      // "t" key: Add current tab
      if (e.key === 't' || e.key === 'T') {
        e.preventDefault();
        addActiveTab();
        return;
      }

      // "1" to "9" key: Copy top item directly
      const num = parseInt(e.key, 10);
      if (num >= 1 && num <= 9) {
        const filtered = getFilteredLinks();
        if (filtered[num - 1]) {
          e.preventDefault();
          const targetItem = filtered[num - 1];
          const targetCard = elements.linksContainer.querySelector(`[data-id="${targetItem.id}"]`);
          copyToClipboard(formatLinkContent(targetItem), targetItem, targetCard);
        }
      }
    });
  }

  // Bootstrap
  document.addEventListener('DOMContentLoaded', init);
})();
