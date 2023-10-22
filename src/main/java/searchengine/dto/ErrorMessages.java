package searchengine.dto;

public class ErrorMessages {
    public static final String IO_OR_NOT_FOUND = "Ошибка ввода/вывода или сайт недоступен ";
    public static final String CONNECTION_TIMED_OUT = "Превышен интервал ожидания страницы: ";
    public static final String ABORTED_BY_USER = "Индексация остановлена пользователем";
    public static final String ERROR_ADD_ENTITY_TO_DB = "Ошибка добавления записи в БД";
    public static final String UNKNOWN_INDEXING_ERROR = "Неизвестная ошибка индексации: ";
    public static final String OUT_OF_SITE = "Данная страница находится за пределами сайтов, " +
            "указанных в конфигурационном файле";
    public static final String INVALID_CHARACTERS_IN_THE_ADDRESS = "Адрес содержит недопустимые символы";
    public static final String INDEXING_HAS_ALREADY_STARTED = "Индексация уже запущена";
    public static final String STOPPING_ERROR = "Ошибка остановки: ";
    public static final String PAGE_IS_NOT_AVAILABLE = "Страница недоступна";
}
