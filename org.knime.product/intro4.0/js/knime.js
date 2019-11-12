window.updateTile = function (data) {
    var carousel = document.getElementById('carousel-content');

    var imagePlaceholder = carousel.getElementsByClassName('image-placeholder');
    for (var placeholderIndex = imagePlaceholder.length - 1; placeholderIndex >= 0; placeholderIndex--) {
        var contentDiv = document.getElementsByClassName('content');
        var imageSrc = data[placeholderIndex]['tile-image'];
        if (imageSrc) {
            var img = document.createElement('img');
            img.src = data[placeholderIndex]['tile-image'];
            imagePlaceholder[placeholderIndex].parentElement.insertBefore(img, contentDiv[placeholderIndex]);
        }
        imagePlaceholder[placeholderIndex].remove();
    }
    
    var carouselTags = carousel.getElementsByClassName('line0');
    for (var tagIndex = carouselTags.length - 1; tagIndex >= 0; tagIndex--) {
        if (data[tagIndex]['tile-tag']) {
            var tileTag = document.createElement('p');
            tileTag.className = 'tile-tag';
            tileTag.innerHTML = data[tagIndex]['tile-tag'];
            carouselTags[tagIndex].appendChild(tileTag);
            carouselTags[tagIndex].className = 'tile-tag-container';
        } else {
            carouselTags[tagIndex].remove();
        }
    }

    var carouselTitles = carousel.getElementsByClassName('line1');
    for (var titleIndex = carouselTitles.length - 1; titleIndex >= 0; titleIndex--) {
        var title = document.createElement('p');
        title.className = 'tile-title';
        title.innerHTML = data[titleIndex]['tile-title'];
        carouselTitles[titleIndex].parentElement.appendChild(title);
        carouselTitles[titleIndex].remove();
    }

    var carouselSubTitles = carousel.getElementsByClassName('line2');
    for (var subTitleIndex = carouselSubTitles.length - 1; subTitleIndex >= 0; subTitleIndex--) {
        if (data[subTitleIndex]['tile-text']) {
            var subtitle = document.createElement('p');
            subtitle.className = 'tile-sub-title';
            subtitle.innerHTML = data[subTitleIndex]['tile-text'];

            var subtitleTooltip = document.createElement('span');
            subtitleTooltip.className = 'tile-sub-title-tooltip';
            subtitleTooltip.innerHTML = data[subTitleIndex]['tile-text'];
            subtitle.appendChild(subtitleTooltip);

            carouselSubTitles[subTitleIndex].parentElement.appendChild(subtitle);
        }
        carouselSubTitles[subTitleIndex].remove();
    }

    var carouselButtons = carousel.getElementsByClassName('button-placeholder');
    for (var buttonIndex = carouselButtons.length - 1; buttonIndex >= 0; buttonIndex--) {
        if (data[buttonIndex]['tile-link'] && data[buttonIndex]['tile-button-text']) {
            var buttonTag = document.createElement('a');
            buttonTag.className = 'button-primary';
            buttonTag.href = data[buttonIndex]['tile-link'];
            buttonTag.innerHTML = data[buttonIndex]['tile-button-text'];
            carouselButtons[buttonIndex].appendChild(buttonTag);
            carouselButtons[buttonIndex].className = 'button-div';
        } else {
            carouselButtons[buttonIndex].remove();
        }
    }
};
window.hideElement = function (id) {
    document.getElementById(id).style.display = 'none';
};

window.displayUpdateTile = function (data) {
    var updateContainer = document.getElementById('update-container');
    if (updateContainer) {
        while (updateContainer.firstChild) {
            updateContainer.removeChild(updateContainer.firstChild);
        }
    } else {
        updateContainer = document.createElement('div');
        updateContainer.id = 'update-container';
    }

    var updateContent = document.createElement('div');
    updateContent.id = 'update-content';
    updateContent.className = 'inner-content';
    updateContainer.appendChild(updateContent);

    var carouselLength = document.createElement('div');
    carouselLength.id = 'carousel-length';
    carouselLength.className = 'carousel-length';
    updateContent.appendChild(carouselLength);

    var updateBannerDiv = document.createElement('div');
    updateBannerDiv.id = 'updateBanner';
    updateBannerDiv.className = 'update-tile';
    carouselLength.appendChild(updateBannerDiv);

    var tileImage = document.createElement('img');
    tileImage.src = data.icon;
    updateBannerDiv.appendChild(tileImage);

    var tileText = document.createElement('p');
    tileText.className = 'tile-text';
    tileText.innerHTML = data.tileContent;
    updateBannerDiv.appendChild(tileText);

    var updateText = document.createElement('p');
    updateText.className = 'tile-text';
    updateText.innerHTML = data.title;
    updateBannerDiv.appendChild(updateText);

    var buttonDiv = document.createElement('div');
    buttonDiv.className = 'button-div';
    updateBannerDiv.appendChild(buttonDiv);

    var updateButton = document.createElement('a');
    updateButton.className = 'button-primary';
    updateButton.href = data.buttonAction;
    updateButton.innerHTML = data.buttonText;
    buttonDiv.appendChild(updateButton);

    var welcomePageWrapper = document.getElementById('welcome-page-wrapper');
    var titleWrapper = document.getElementById('title-wrapper');
    titleWrapper.setAttribute('style', 'background: #FFF');
    var contentContainer = document.getElementById('content-container');
    welcomePageWrapper.insertBefore(updateContainer, contentContainer);
};

// prevent right click on the welcome page
document.addEventListener('contextmenu', function (event) { event.preventDefault(); });
