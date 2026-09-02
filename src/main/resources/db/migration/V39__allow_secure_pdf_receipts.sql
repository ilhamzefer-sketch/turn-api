alter table secure_attachments drop constraint if exists chk_secure_attachments_media;
alter table secure_attachments add constraint chk_secure_attachments_media check (
    (media_type = 'image/jpeg' and file_extension = 'jpg')
    or (media_type = 'image/png' and file_extension = 'png')
    or (purpose = 'PAYMENT_RECEIPT' and media_type = 'application/pdf' and file_extension = 'pdf')
);

alter table secure_attachments drop constraint if exists chk_secure_attachments_dimensions;
alter table secure_attachments add constraint chk_secure_attachments_dimensions check (
    ((media_type = 'image/jpeg' or media_type = 'image/png')
        and width_pixels between 1 and 10000
        and height_pixels between 1 and 10000
        and cast(width_pixels as bigint) * cast(height_pixels as bigint) <= 25000000)
    or (media_type = 'application/pdf' and width_pixels = 0 and height_pixels = 0)
);
